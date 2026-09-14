package com.ailearn.platform.core.operationaudit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 领域应用服务提交成功业务操作审计时使用的最小结构化命令。 */
public record OperationAuditCommand(
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
        String idempotencyKey,
        OffsetDateTime occurredAt) {
}
