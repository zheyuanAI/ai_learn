package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.dto.TraceabilityProjection;
import com.ailearn.platform.core.traceability.dto.TraceabilityQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 使用阶段 7 追溯应用服务实现的 AI 只读追溯工具。 */
@Component
public class QueryTraceAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of(
            "SALES_ORDER", "PURCHASE_ORDER", "WORK_ORDER", "DEVICE_ALARM", "INVENTORY_BATCH");
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("entity_type", "entity_id");

    private final Supplier<TraceabilityApplicationService> traceabilityServiceProvider;

    /**
     * 注入可选追溯应用服务；即使 IoT 事实源未启用，也要保留稳定的 AI 工具目录并在调用时返回受控失败。
     */
    @Autowired
    public QueryTraceAiTool(ObjectProvider<TraceabilityApplicationService> traceabilityServiceProvider) {
        this.traceabilityServiceProvider = traceabilityServiceProvider::getIfAvailable;
    }

    /** 测试与显式装配入口，直接使用已存在的追溯应用服务。 */
    public QueryTraceAiTool(TraceabilityApplicationService traceabilityService) {
        this.traceabilityServiceProvider = () -> traceabilityService;
    }

    @Override
    public String name() {
        return "queryTrace";
    }

    @Override
    public String description() {
        return "查询当前用户有权查看的销售、采购、工单、设备告警或库存批次上下游追溯事实";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "entity_type", Map.of("type", "string", "enum", ALLOWED_ENTITY_TYPES),
                        "entity_id", Map.of("type", "string", "format", "uuid")
                ),
                "required", List.of("entity_type", "entity_id")
        );
    }

    @Override
    public Set<String> anyOfPermissions() {
        return Set.of("trace:chain:view", "ai:trace:view");
    }

    /**
     * 用途：查询当前用户有权查看的黄金闭环节点。
     * 入参：可信 AI 上下文和实体类型/UUID；出参：追溯投影和来源时间；流程：严格参数白名单后调用既有追溯服务。
     */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (!ALLOWED_ARGUMENTS.containsAll(safeArguments.keySet())) {
            throw new IllegalArgumentException("queryTrace 只允许 entity_type 和 entity_id 参数");
        }
        String entityType = requiredText(safeArguments, "entity_type").toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_ENTITY_TYPES.contains(entityType)) {
            throw new IllegalArgumentException("queryTrace 不支持实体类型: " + entityType);
        }
        UUID entityId = parseUuid(requiredText(safeArguments, "entity_id"));
        TraceabilityApplicationService traceabilityService = traceabilityServiceProvider.get();
        if (traceabilityService == null) {
            throw new IllegalStateException("追溯事实源未启用，暂时无法执行 queryTrace");
        }
        TraceabilityProjection projection = traceabilityService.query(
                new TraceabilityQuery(context.toFactsContext(), entityType, entityId));
        String sourceSummary = "traceability facts: " + projection.nodes().size() + " nodes"
                + (projection.missingSources().isEmpty() ? "" : ", missing=" + projection.missingSources());
        Instant sourceUpdatedAt = projection.sourceUpdatedAt();
        String timeRangeSummary = sourceUpdatedAt == null ? "" : "数据更新至 " + sourceUpdatedAt;
        return new AiToolResult(name(), projection, sourceSummary, timeRangeSummary,
                List.of(), AiToolStatus.Success);
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
}
