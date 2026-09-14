package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 查询工单生命周期、工序完成度与数量进度的 AI 只读工具。 */
@Component
public class QueryWorkOrderProgressAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("work_order_id");
    private final WorkOrderExecutionService workOrderExecutionService;

    /** 注入现有工单生命周期应用端口。 */
    public QueryWorkOrderProgressAiTool(WorkOrderExecutionService workOrderExecutionService) {
        this.workOrderExecutionService = workOrderExecutionService;
    }

    @Override public String name() { return "queryWorkOrderProgress"; }

    @Override public String description() {
        return "查询指定工单的状态、计划数量、工序完成度、报工/合格/不良/入库数量以及质量或库存阻塞标记";
    }

    @Override public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("work_order_id", Map.of("type", "string", "format", "uuid")),
                "required", List.of("work_order_id"));
    }

    @Override public Set<String> anyOfPermissions() { return Set.of("mes:workorder:view"); }

    /** 查询工单聚合并移除原始完成会话和允许写动作。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        UUID workOrderId = AiToolArguments.requiredUuid(safe, "work_order_id");
        WorkOrderLifecycle lifecycle = workOrderExecutionService.find(workOrderId)
                .orElseThrow(() -> new IllegalArgumentException("工单不存在或当前租户不可见"));
        WorkOrderProgress progress = lifecycle.progress();
        WorkOrderProgressFact fact = new WorkOrderProgressFact(lifecycle.workOrder().id(),
                lifecycle.workOrder().workOrderNo(), lifecycle.workOrder().productId(),
                lifecycle.workOrder().plannedQty().toPlainString(), lifecycle.status().name(),
                lifecycle.requiredOperationIds().size(), progress.completedOperationIds().size(),
                progress.reportedQty().toPlainString(), progress.qualifiedQty().toPlainString(),
                progress.defectQty().toPlainString(), progress.receivedQty().toPlainString(),
                progress.qualityBlocked(), progress.pendingInventoryCommands(), lifecycle.rejectionReason(),
                lifecycle.completionType() == null ? null : lifecycle.completionType().name(),
                lifecycle.completionReason(), lifecycle.completedAt());
        String time = lifecycle.completedAt() == null ? "" : "工单完成于 " + lifecycle.completedAt();
        return new AiToolResult(name(), fact, "work order lifecycle: " + lifecycle.workOrder().workOrderNo(),
                time, List.of(), AiToolStatus.Success);
    }

    /** 面向模型的工单进度事实。 */
    public record WorkOrderProgressFact(UUID workOrderId, String workOrderNo, UUID productId,
                                        String plannedQty, String status, int requiredOperationCount,
                                        int completedOperationCount, String reportedQty, String qualifiedQty,
                                        String defectQty, String receivedQty, boolean qualityBlocked,
                                        boolean pendingInventoryCommands, String rejectionReason,
                                        String completionType, String completionReason,
                                        OffsetDateTime completedAt) { }
}
