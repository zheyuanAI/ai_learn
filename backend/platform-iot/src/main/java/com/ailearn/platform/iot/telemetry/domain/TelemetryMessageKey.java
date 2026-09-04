package com.ailearn.platform.iot.telemetry.domain;

import java.util.UUID;

/**
 * MQTT 消息去重键。
 * 键同时携带租户和设备边界；业务部分优先使用 message_id，否则使用 sequence。
 */
public record TelemetryMessageKey(UUID tenantId, UUID deviceId, KeyType keyType, String value,
                                  String messageId, Long sequenceNo) {

    /** 兼容只提供优先去重键的旧测试/调用方；未提供的消息标识字段保持为空。 */
    public TelemetryMessageKey(UUID tenantId, UUID deviceId, KeyType keyType, String value) {
        this(tenantId, deviceId, keyType,
                value, keyType == KeyType.MESSAGE_ID ? value : null,
                keyType == KeyType.SEQUENCE ? Long.valueOf(value) : null);
    }

    /** 消息去重标识类型。 */
    public enum KeyType {
        MESSAGE_ID,
        SEQUENCE
    }

    /**
     * 用途：生成数据库兼容的可审计键文本；入参为去重键组成部分；出参包含设备 ID。
     */
    public String asText() {
        return deviceId + "|" + (keyType == KeyType.MESSAGE_ID ? "message_id" : "sequence") + "|" + value;
    }
}
