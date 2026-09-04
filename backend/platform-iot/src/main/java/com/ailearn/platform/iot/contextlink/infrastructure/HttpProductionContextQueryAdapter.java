package com.ailearn.platform.iot.contextlink.infrastructure;

import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.ailearn.platform.iot.contextlink.domain.port.ProductionContextQueryPort;
import com.ailearn.platform.iot.contextlink.dto.ProductionContextQueryResponse;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 可选的 Core HTTP 只读适配器。
 * 只有同时配置 query-url 和 HMAC secret 才启用；请求只含服务身份签名，不透传用户身份、权限或租户 Header。
 */
@Component
@ConditionalOnProperty(name = {"iot.context.core.query-url", "iot.context.core.hmac-secret"})
public class HttpProductionContextQueryAdapter implements ProductionContextQueryPort {
    private static final String SERVICE_NAME = "platform-iot";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final RestTemplate restTemplate;
    private final String queryUrl;
    private final String hmacSecret;
    private final Clock clock;

    public HttpProductionContextQueryAdapter(
            @Value("${iot.context.core.query-url}") String queryUrl,
            @Value("${iot.context.core.hmac-secret}") String hmacSecret) {
        this(queryUrl, hmacSecret, defaultRestTemplate(), Clock.systemUTC());
    }

    /** 测试构造入口；生产构造默认使用 UTC 时钟。 */
    HttpProductionContextQueryAdapter(String queryUrl, String hmacSecret,
                                      RestTemplate restTemplate, Clock clock) {
        if (queryUrl == null || queryUrl.isBlank() || hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("Core 上下文查询地址和 HMAC 密钥不能为空");
        }
        this.queryUrl = queryUrl;
        this.hmacSecret = hmacSecret;
        this.restTemplate = restTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<ProductionContextView> findActive(UUID tenantId, UUID deviceId, OffsetDateTime alarmTime) {
        if (tenantId == null || deviceId == null || alarmTime == null) {
            throw new IllegalArgumentException("tenantId、deviceId、alarmTime 均不能为空");
        }
        String canonical = canonical(tenantId, deviceId, alarmTime);
        OffsetDateTime signedAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Name", SERVICE_NAME);
        headers.set("X-Service-Timestamp", signedAt.toString());
        headers.set("X-Service-Signature", sign(canonical + "\n" + signedAt));
        URI uri = UriComponentsBuilder.fromUriString(queryUrl)
                .queryParam("tenant_id", tenantId)
                .queryParam("device_id", deviceId)
                .queryParam("alarm_time", alarmTime)
                .build(true).toUri();
        try {
            ResponseEntity<ProductionContextQueryResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), ProductionContextQueryResponse.class);
            return Optional.ofNullable(response.getBody()).map(ProductionContextQueryResponse::toView);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (HttpClientErrorException.Conflict exception) {
            throw new IllegalStateException("Core 返回多个活动工序，拒绝自动补链", exception);
        } catch (RestClientException exception) {
            throw new ServiceUnavailableException("Core ProductionContextQuery 暂时不可用", exception);
        }
    }

    /** 固定线协议签名原文，确保租户、设备和告警时刻不可被代理层替换。 */
    static String canonical(UUID tenantId, UUID deviceId, OffsetDateTime alarmTime) {
        return tenantId + "\n" + deviceId + "\n" + alarmTime.toInstant();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JDK 不支持 HmacSHA256", exception);
        }
    }

    /** 为 Core 故障提供有界超时；超时异常会交给补链任务重试，不阻塞遥测主链路。 */
    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }
}
