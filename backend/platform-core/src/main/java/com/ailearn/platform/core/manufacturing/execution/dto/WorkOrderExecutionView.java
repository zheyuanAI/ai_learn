package com.ailearn.platform.core.manufacturing.execution.dto;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 工单列表与详情查询视图。
 * <p>
 * 用途：把生命周期聚合中的基础工单、执行累计量和当前用户可用动作展平成前端契约；不允许前端根据状态自行推断动作权限。
 * 入参：当前租户已校验的工单生命周期；出参：扁平工单查询字段和服务端动作能力；流程：读取聚合快照，按状态和当前权限生成动作描述。
 * </p>
 */
public record WorkOrderExecutionView(
        UUID id,
        UUID tenantId,
        String workOrderNo,
        UUID productId,
        BigDecimal plannedQty,
        OffsetDateTime plannedStartTime,
        OffsetDateTime plannedFinishTime,
        UUID bomId,
        String bomVersion,
        UUID routingId,
        String routingVersion,
        UUID sourceSalesOrderLineId,
        String status,
        boolean deleted,
        UUID createdBy,
        OffsetDateTime createdAt,
        long version,
        UUID submittedBy,
        OffsetDateTime submittedAt,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        String rejectionReason,
        String completionType,
        String completionReason,
        UUID completedBy,
        String completedSessionId,
        OffsetDateTime completedAt,
        BigDecimal reportedQty,
        BigDecimal qualifiedQty,
        BigDecimal defectQty,
        BigDecimal receivedQty,
        List<AllowedActionVo> allowedActions) {

    public WorkOrderExecutionView {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /**
     * 将生命周期聚合转换为页面查询视图。
     * 入参：已按租户隔离的生命周期聚合；出参：扁平工单视图；流程：复制工单事实、进度快照并计算动作权限。
     */
    public static WorkOrderExecutionView from(WorkOrderLifecycle lifecycle) {
        WorkOrderFact workOrder = lifecycle.workOrder();
        return new WorkOrderExecutionView(workOrder.id(), workOrder.tenantId(), workOrder.workOrderNo(),
                workOrder.productId(), workOrder.plannedQty(), workOrder.plannedStartTime(),
                workOrder.plannedFinishTime(), workOrder.bomId(), workOrder.bomVersion(), workOrder.routingId(),
                workOrder.routingVersion(), workOrder.sourceSalesOrderLineId(), lifecycle.status().name(),
                workOrder.deleted(), workOrder.createdBy(), workOrder.createdAt(), workOrder.version(),
                lifecycle.submittedBy(), lifecycle.submittedAt(), lifecycle.reviewedBy(), lifecycle.reviewedAt(),
                lifecycle.rejectionReason(), lifecycle.completionType() == null ? null : lifecycle.completionType().name(),
                lifecycle.completionReason(), lifecycle.completedBy(), lifecycle.completedSessionId(),
                lifecycle.completedAt(), lifecycle.progress().reportedQty(), lifecycle.progress().qualifiedQty(),
                lifecycle.progress().defectQty(), lifecycle.progress().receivedQty(), actions(lifecycle));
    }

    /** 根据状态机允许动作与当前登录权限生成前端统一动作对象。 */
    private static List<AllowedActionVo> actions(WorkOrderLifecycle lifecycle) {
        Set<String> supportedActions = Set.of("submit", "approve", "reject", "complete", "manualComplete");
        return lifecycle.allowedActions().stream()
                .filter(supportedActions::contains)
                .sorted()
                .map(WorkOrderExecutionView::action)
                .toList();
    }

    /** 将动作映射到真实接口权限，权限缺失时返回禁用原因而不是隐藏状态能力。 */
    private static AllowedActionVo action(String name) {
        String permission = switch (name) {
            case "submit" -> "mes:workorder:submit";
            case "approve", "reject" -> "mes:workorder:approve";
            case "complete", "manualComplete" -> "mes:workorder:complete";
            default -> "";
        };
        boolean enabled = UserContextHolder.hasPermission(permission);
        return new AllowedActionVo(name, enabled, enabled ? null : "当前用户没有执行该操作的权限");
    }
}
