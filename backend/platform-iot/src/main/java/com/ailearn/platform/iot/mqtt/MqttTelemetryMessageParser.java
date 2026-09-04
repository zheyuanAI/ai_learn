package com.ailearn.platform.iot.mqtt;

import com.ailearn.platform.iot.credential.application.DeviceCredentialVerifier;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.telemetry.application.TelemetryCredentialContext;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetryMetric;
import com.ailearn.platform.iot.telemetry.exception.TelemetryErrorCode;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

/**
 * MQTT 主题和载荷适配器。
 * <p>
 * 主题采用 {@code devices/{credential_reference}/telemetry}。凭证引用只作为内部定位键，租户和设备身份从已校验
 * 的凭证记录恢复；载荷中的 tenant_id、device_id 和 device_code 只能作为一致性断言，绝不覆盖可信上下文。
 * </p>
 */
public class MqttTelemetryMessageParser {
    private static final String TOPIC_PREFIX = "devices/";
    private static final String TOPIC_SUFFIX = "/telemetry";
    private static final int MAX_CREDENTIAL_REFERENCE_LENGTH = 96;

    private final DeviceCredentialVerifier credentialVerifier;
    private final MqttTelemetryConsumer consumer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 用途：组装生产 MQTT 消息解析链；入参为凭证校验端口、统一消费端口和 Spring Jackson 配置。
     */
    public MqttTelemetryMessageParser(DeviceCredentialVerifier credentialVerifier,
                                      MqttTelemetryConsumer consumer, ObjectMapper objectMapper) {
        this(credentialVerifier, consumer, objectMapper, Clock.systemUTC());
    }

    /**
     * 测试构造入口；允许固定平台时间，验证接收时间和摘要行为而不依赖系统时钟。
     */
    MqttTelemetryMessageParser(DeviceCredentialVerifier credentialVerifier, MqttTelemetryConsumer consumer,
                               ObjectMapper objectMapper, Clock clock) {
        this.credentialVerifier = credentialVerifier;
        this.consumer = consumer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 用途：处理一条 Broker 已接收的 MQTT 消息；入参为主题和原始 UTF-8 载荷；出参为统一遥测摄取结果。
     * 流程：解析主题凭证 -> 校验凭证和设备归属 -> 解析载荷 -> 计算原始摘要 -> 委托既有摄取服务。
     */
    public TelemetryIngestionResult accept(String topic, byte[] payload) {
        String credentialReference = credentialReference(topic);
        Device device = credentialVerifier.verifyReference(credentialReference);
        JsonNode root = parsePayload(payload);
        validateOptionalIdentityClaims(root, credentialReference, device);

        OffsetDateTime receivedAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        TelemetryIngestionCommand command = new TelemetryIngestionCommand(
                new TelemetryCredentialContext(device.tenantId(), device.id(), credentialReference),
                device.id(), device.deviceCode(), timestamp(root), receivedAt,
                text(root, "message_id"), sequence(root), metrics(root), sha256(payload));
        return consumer.consume(command);
    }

    /** 解析并限制凭证主题，避免通配符、跨层级或任意主题进入业务摄取链。 */
    private String credentialReference(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX) || !topic.endsWith(TOPIC_SUFFIX)) {
            throw invalid("MQTT 主题必须为 devices/{credential_reference}/telemetry");
        }
        String reference = topic.substring(TOPIC_PREFIX.length(), topic.length() - TOPIC_SUFFIX.length());
        if (reference.isBlank() || reference.contains("/") || reference.contains("+") || reference.contains("#")
                || reference.length() > MAX_CREDENTIAL_REFERENCE_LENGTH || !reference.equals(reference.trim())) {
            throw invalid("MQTT 主题中的 credential_reference 无效");
        }
        return reference;
    }

    /** 只接受 JSON 对象；读取失败统一转为稳定的遥测格式错误。 */
    private JsonNode parsePayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw invalid("MQTT 载荷不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw invalid("MQTT 载荷必须是 JSON 对象");
            }
            return root;
        } catch (IOException exception) {
            throw invalid("MQTT 载荷不是合法 JSON");
        }
    }

    /** 将可选的客户端身份字段作为断言校验，防止伪造字段悄悄与主题身份不一致。 */
    private void validateOptionalIdentityClaims(JsonNode root, String credentialReference, Device device) {
        String claimedCredential = text(root, "credential_reference");
        if (claimedCredential != null && !credentialReference.equals(claimedCredential)) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, "消息凭证与 MQTT 主题不匹配");
        }
        assertClaim(root, "tenant_id", device.tenantId().toString(), true);
        assertClaim(root, "device_id", device.id().toString(), true);
        assertClaim(root, "device_code", device.deviceCode(), false);
    }

    private void assertClaim(JsonNode root, String field, String expected, boolean ignoreCase) {
        String claimed = text(root, field);
        if (claimed == null) {
            return;
        }
        boolean matches = ignoreCase ? expected.equalsIgnoreCase(claimed) : expected.equals(claimed);
        if (!matches) {
            throw new IotException(IotErrorCode.TENANT_VIOLATION, "消息身份字段与已认证设备不匹配");
        }
    }

    /** 读取设备采集时间；解析失败时在任何事实写入前拒绝整条消息。 */
    private OffsetDateTime timestamp(JsonNode root) {
        String value = text(root, "ts");
        if (value == null || value.isBlank()) {
            throw invalid("ts 不能为空");
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw invalid("ts 必须是 ISO-8601 时间");
        }
    }

    /** 读取可选序号，保留摄取服务对负数和消息键缺失的统一判断。 */
    private Long sequence(JsonNode root) {
        JsonNode value = root.get("sequence");
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return Long.parseLong(value.asText());
        } catch (RuntimeException exception) {
            throw invalid("sequence 必须是整数");
        }
    }

    /** 将指标数组转换为既有应用命令，不在 MQTT 适配层复制指标白名单和类型校验。 */
    private List<TelemetryMetric> metrics(JsonNode root) {
        JsonNode value = root.get("metrics");
        if (value == null || !value.isArray()) {
            throw invalid("metrics 必须是数组");
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<List<TelemetryMetric>>() { });
        } catch (IllegalArgumentException exception) {
            throw invalid("metrics 格式无效");
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    /** 以原始字节计算 SHA-256，确保去重链路收到稳定且不包含敏感字段的载荷摘要。 */
    private String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    private TelemetryException invalid(String detail) {
        return new TelemetryException(TelemetryErrorCode.INVALID_MESSAGE, detail);
    }
}
