package com.ailearn.platform.iot.telemetry.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 规范化后的单指标遥测事实。
 * 原始设备时间、平台接收时间和消息去重信息均保留，事实只追加不覆盖。
 */
public record TelemetryFact(UUID id, UUID tenantId, UUID deviceId, String messageKey,
                            String messageId, Long sequence, OffsetDateTime timestamp,
                            OffsetDateTime receivedAt, String metricCode, String metricValue,
                            String metricUnit) {
}
