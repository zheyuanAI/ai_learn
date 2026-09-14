package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** AI 查询当前租户业务对象结构化操作历史的受控只读工具。 */
@Component
public class QueryOperationAuditAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "entity_type", "entity_id", "occurred_from", "occurred_to", "limit");
    private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of("SALES_ORDER");

    private final OperationAuditApplicationService operationAuditService;
    /** 注入 Core 操作审计应用服务。 */
    public QueryOperationAuditAiTool(OperationAuditApplicationService operationAuditService) {
        this.operationAuditService = operationAuditService;
    }

    @Override
    public String name() {
        return "queryOperationAudit";
    }

    @Override
    public String description() {
        return "查询当前租户销售订单的结构化操作时间线，说明哪个账号在何时执行了什么动作";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "entity_type", Map.of("type", "string", "enum", ALLOWED_ENTITY_TYPES),
                        "entity_id", Map.of("type", "string", "format", "uuid"),
                        "occurred_from", Map.of("type", "string", "format", "date-time"),
                        "occurred_to", Map.of("type", "string", "format", "date-time"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)
                ),
                "required", List.of("entity_type", "entity_id")
        );
    }

    @Override
    public Set<String> anyOfPermissions() {
        return Set.of("sales:order:view");
    }

    /**
     * 用途：返回当前用户有权查看对象的脱敏操作时间线。
     * 入参：可信上下文、实体 UUID、可选时间范围和条数；出参：不含原始幂等键和原始会话 JTI 的工具结果。
     */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (!ALLOWED_ARGUMENTS.containsAll(safeArguments.keySet())) {
            throw new IllegalArgumentException("queryOperationAudit 包含未允许参数");
        }
        String entityType = requiredText(safeArguments, "entity_type").toUpperCase(Locale.ROOT);
        if (!ALLOWED_ENTITY_TYPES.contains(entityType)) {
            throw new IllegalArgumentException("queryOperationAudit 暂不支持实体类型: " + entityType);
        }
        UUID entityId = parseUuid(requiredText(safeArguments, "entity_id"));
        OffsetDateTime occurredFrom = parseTime(safeArguments.get("occurred_from"), "occurred_from");
        OffsetDateTime occurredTo = parseTime(safeArguments.get("occurred_to"), "occurred_to");
        int limit = parseLimit(safeArguments.get("limit"));
        List<OperationAuditEntry> entries = operationAuditService.query(new OperationAuditQuery(
                context.tenantId(), entityType, entityId, occurredFrom, occurredTo, limit));
        List<OperationTimelineItem> timeline = entries.stream().map(QueryOperationAuditAiTool::sanitize).toList();
        String timeRange = timeRange(entries);
        return new AiToolResult(name(), Map.of(
                "entityType", entityType,
                "entityId", entityId,
                "timeline", timeline,
                "identityNotice", "记录识别的是账号、用户 ID 和会话引用；共享账号时不能据此识别实际自然人"
        ), "core operation audit: " + timeline.size() + " records", timeRange,
                List.of(), AiToolStatus.Success);
    }

    private static OperationTimelineItem sanitize(OperationAuditEntry entry) {
        return new OperationTimelineItem(entry.actorType(), entry.actorId(), entry.actorAccount(),
                sessionReference(entry.sessionId()), entry.actionCode(), entry.entityNo(),
                entry.beforeStatus(), entry.afterStatus(), entry.operationSummary(),
                entry.changedFieldsSummary(), entry.result().name(), entry.errorCode(),
                entry.occurredAt());
    }

    private static String timeRange(List<OperationAuditEntry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        OffsetDateTime earliest = entries.stream().map(OperationAuditEntry::occurredAt)
                .min(OffsetDateTime::compareTo).orElseThrow();
        OffsetDateTime latest = entries.stream().map(OperationAuditEntry::occurredAt)
                .max(OffsetDateTime::compareTo).orElseThrow();
        return "操作发生于 " + earliest + " 至 " + latest;
    }

    private static String sessionReference(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8)));
            return "session-" + digest.substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    private static String requiredText(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return text;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("entity_id 必须是有效 UUID", exception);
        }
    }

    private static OffsetDateTime parseTime(Object value, String name) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(String.valueOf(value).trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " 必须是包含时区的 ISO-8601 时间", exception);
        }
    }

    private static int parseLimit(Object value) {
        if (value == null) {
            return 50;
        }
        try {
            int limit = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
            if (limit < 1 || limit > 100) {
                throw new IllegalArgumentException("limit 必须在 1 到 100 之间");
            }
            return limit;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit 必须是整数", exception);
        }
    }

    /** 发送给模型的脱敏时间线，不含审计主键、请求 ID、幂等键摘要和原始会话 ID。 */
    public record OperationTimelineItem(String actorType, UUID actorId, String actorAccount,
                                        String sessionReference, String actionCode, String entityNo,
                                        String beforeStatus, String afterStatus, String operationSummary,
                                        String changedFieldsSummary, String result, String errorCode,
                                        OffsetDateTime occurredAt) {
    }
}
