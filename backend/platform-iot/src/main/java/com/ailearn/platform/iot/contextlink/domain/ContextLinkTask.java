package com.ailearn.platform.iot.contextlink.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 告警生产上下文补链任务；任务状态与告警事实分离，便于失败重试。 */
public record ContextLinkTask(UUID id, UUID tenantId, UUID alarmId, String status,
                              int retryCount, OffsetDateTime nextRetryAt) {
}
