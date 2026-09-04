package com.ailearn.platform.core.manufacturing.contextquery.controller;

import com.ailearn.platform.core.manufacturing.contextquery.domain.ProductionContext;
import com.ailearn.platform.core.manufacturing.contextquery.port.ProductionContextQuery;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Core 给 IoT 上下文补链使用的最小内部查询入口。
 * <p>
 * 该入口不接受用户身份 Header，只有在配置 HMAC 密钥后才会装配；调用方必须证明服务身份、签名查询参数并
 * 通过时间窗口校验。业务事实查询仍由 {@link ProductionContextQuery} 负责，未找到上下文返回 404，多匹配由
 * 领域异常映射为稳定冲突响应。
 * </p>
 */
@RestController
@RequestMapping("/internal/production-context")
@ConditionalOnProperty(name = "core.context.iot.hmac-secret")
public class ProductionContextInternalController {
    private static final String EXPECTED_SERVICE = "platform-iot";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    private final ProductionContextQuery query;
    private final byte[] secret;

    /**
     * 注入生产上下文只读端口与服务间 HMAC 密钥。
     *
     * @param query 生产上下文查询端口
     * @param hmacSecret IoT 服务间共享密钥
     */
    public ProductionContextInternalController(
            ProductionContextQuery query,
            @Value("${core.context.iot.hmac-secret}") String hmacSecret) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("Core-IoT 上下文 HMAC 密钥不能为空");
        }
        this.query = query;
        this.secret = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 查询告警时刻的唯一活动生产上下文。
     * 入参：租户、设备、告警时刻和服务签名 Header；出参：最小上下文摘要或 404；流程：验签、验时、调用只读端口。
     *
     * @param tenantId IoT 已认证设备所属租户
     * @param deviceId IoT 已认证设备标识
     * @param alarmTime 告警设备时间
     * @param serviceName 调用服务名
     * @param serviceTimestamp 签名生成时间
     * @param signature HMAC-SHA256 Base64 签名
     * @return 上下文摘要或未找到
     */
    @GetMapping
    public ResponseEntity<ProductionContextResponse> query(
            @RequestParam("tenant_id") UUID tenantId,
            @RequestParam("device_id") UUID deviceId,
            @RequestParam("alarm_time") OffsetDateTime alarmTime,
            @RequestHeader("X-Service-Name") String serviceName,
            @RequestHeader("X-Service-Timestamp") String serviceTimestamp,
            @RequestHeader("X-Service-Signature") String signature) {
        OffsetDateTime signedAt = verify(serviceName, serviceTimestamp, signature,
                canonical(tenantId, deviceId, alarmTime));
        // signedAt 已在 verify 中完成时钟偏差校验；保留局部变量使签名校验流程清晰。
        if (signedAt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return query.findActive(tenantId, deviceId, alarmTime)
                .map(ProductionContextResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 校验服务身份、时间窗口与参数签名，非法请求统一按未认证处理。 */
    private OffsetDateTime verify(String serviceName, String serviceTimestamp, String signature,
                                  String canonicalQuery) {
        if (!EXPECTED_SERVICE.equals(serviceName) || serviceTimestamp == null || signature == null) {
            return null;
        }
        final OffsetDateTime signedAt;
        try {
            signedAt = OffsetDateTime.parse(serviceTimestamp).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return null;
        }
        Duration skew = Duration.between(signedAt, OffsetDateTime.now(ZoneOffset.UTC)).abs();
        if (skew.compareTo(MAX_CLOCK_SKEW) > 0) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] expected = mac.doFinal((canonicalQuery + "\n" + serviceTimestamp)
                    .getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(expected, actual) ? signedAt : null;
        } catch (Exception exception) {
            return null;
        }
    }

    /** 固定租户、设备和告警时刻的签名原文，避免代理替换查询范围。 */
    private static String canonical(UUID tenantId, UUID deviceId, OffsetDateTime alarmTime) {
        return tenantId + "\n" + deviceId + "\n" + alarmTime.toInstant();
    }

    /** Core 与 IoT 之间的 snake_case 线协议响应。 */
    public record ProductionContextResponse(
            @JsonProperty("tenant_id") UUID tenantId,
            @JsonProperty("device_id") UUID deviceId,
            @JsonProperty("work_order_id") UUID workOrderId,
            @JsonProperty("operation_execution_id") UUID operationExecutionId,
            @JsonProperty("operation_id") UUID operationId,
            @JsonProperty("started_at") OffsetDateTime startedAt,
            @JsonProperty("event_at") OffsetDateTime eventAt) {

        /** 从 Core 领域摘要转为不泄露内部类型的线协议 DTO。 */
        public static ProductionContextResponse from(ProductionContext value) {
            return new ProductionContextResponse(value.tenantId(), value.deviceId(), value.workOrderId(),
                    value.operationExecutionId(), value.operationId(), value.startedAt(), value.eventAt());
        }
    }
}
