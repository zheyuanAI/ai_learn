package com.ailearn.platform.core.ai.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 一次 AI 受控工具调用的脱敏审计记录。 */
public record ToolAuditEntry(UUID id, UUID tenantId, UUID userId, UUID sessionId,
                             String requestId, String toolName, String inputSummary,
                             String outputSummary, String sourceSummary,
                             String timeRangeSummary, String toolCallSummary,
                             String modelId, long durationMs, AiToolStatus status,
                             String errorCode, String errorReason, OffsetDateTime createdAt) {
}
