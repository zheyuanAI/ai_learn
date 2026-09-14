package com.ailearn.platform.core.operationaudit.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Core 服务拥有的结构化业务操作审计记录。 */
public record OperationAuditEntry(
        UUID id,
        UUID tenantId,
        String actorType,
        UUID actorId,
        String actorAccount,
        String sessionId,
        String requestId,
        String actionCode,
        String entityType,
        UUID entityId,
        String entityNo,
        String beforeStatus,
        String afterStatus,
        String operationSummary,
        String changedFieldsSummary,
        OperationAuditResult result,
        String errorCode,
        String errorReason,
        String idempotencyKeyHash,
        OffsetDateTime occurredAt) {

    public OperationAuditEntry {
        if (id == null || tenantId == null || entityId == null || occurredAt == null) {
            throw new IllegalArgumentException("操作审计缺少标识、租户、实体或发生时间");
        }
        if (actorType == null || actorType.isBlank() || requestId == null || requestId.isBlank()
                || actionCode == null || actionCode.isBlank() || entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("操作审计缺少操作者类型、请求、动作或实体类型");
        }
        operationSummary = operationSummary == null ? "" : operationSummary;
        changedFieldsSummary = changedFieldsSummary == null ? "" : changedFieldsSummary;
        result = result == null ? OperationAuditResult.SUCCESS : result;
    }
}
