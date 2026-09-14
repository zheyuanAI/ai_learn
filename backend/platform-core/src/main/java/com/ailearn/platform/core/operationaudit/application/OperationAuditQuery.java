package com.ailearn.platform.core.operationaudit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 由可信租户限定的业务对象操作时间线查询。 */
public record OperationAuditQuery(UUID tenantId, String entityType, UUID entityId,
                                  OffsetDateTime occurredFrom, OffsetDateTime occurredTo, int limit) {
    public OperationAuditQuery {
        if (tenantId == null || entityId == null || entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("操作审计查询缺少租户或业务对象");
        }
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new IllegalArgumentException("occurred_from 不能晚于 occurred_to");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
        }
    }
}
