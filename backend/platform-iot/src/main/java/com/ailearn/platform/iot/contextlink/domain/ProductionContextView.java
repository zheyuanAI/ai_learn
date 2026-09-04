package com.ailearn.platform.iot.contextlink.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core 生产上下文的 IoT 侧只读 DTO；仅携带告警补链所需的业务标识和时间。
 */
public record ProductionContextView(UUID tenantId, UUID deviceId, UUID workOrderId,
                                    UUID operationExecutionId, UUID operationId,
                                    OffsetDateTime startedAt, OffsetDateTime eventAt) {
    public ProductionContextView {
        if (tenantId == null || deviceId == null || workOrderId == null
                || operationExecutionId == null || operationId == null
                || startedAt == null || eventAt == null) {
            throw new IllegalArgumentException("生产上下文摘要字段不完整");
        }
    }
}
