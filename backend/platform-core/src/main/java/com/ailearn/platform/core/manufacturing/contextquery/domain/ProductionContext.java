package com.ailearn.platform.core.manufacturing.contextquery.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * IoT/告警补链使用的最小生产上下文摘要，不携带用户 Header、权限或遥测内容。
 */
public record ProductionContext(UUID tenantId, UUID deviceId, UUID workOrderId,
                                UUID operationExecutionId, UUID operationId,
                                OffsetDateTime startedAt, OffsetDateTime eventAt) {
    public ProductionContext {
        if (tenantId == null || deviceId == null || workOrderId == null
                || operationExecutionId == null || operationId == null
                || startedAt == null || eventAt == null) {
            throw new IllegalArgumentException("生产上下文摘要字段不完整");
        }
    }
}
