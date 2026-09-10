package com.ailearn.platform.core.manufacturing.operation.dto;

import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionEvent;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionStatus;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 工序执行列表查询视图。
 *
 * 用途：把执行聚合转换成页面稳定字段，并返回服务端动作能力。
 * 入参：当前租户已校验的执行聚合；出参：执行编号、时间线字段及 allowedActions。
 * 流程：从执行事件提取首个开工和最后完工时间，再按状态与权限计算动作。
 */
public record OperationExecutionView(
        UUID id,
        UUID tenantId,
        String executionNo,
        UUID dispatchOrderId,
        String dispatchNo,
        UUID workOrderId,
        String workOrderNo,
        String productName,
        UUID operationId,
        Integer operationNo,
        String operationName,
        UUID operatorId,
        String operatorName,
        UUID deviceId,
        String deviceName,
        String deviceCode,
        OperationExecutionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime pausedAt,
        OffsetDateTime resumedAt,
        OffsetDateTime completedAt,
        String reportedQty,
        long version,
        List<AllowedActionVo> allowedActions) {

    public OperationExecutionView {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /**
     * 将执行聚合转换为页面视图。
     * 入参：同租户工序执行事实；出参：页面执行视图；流程：提取事件时间并生成动作能力。
     */
    public static OperationExecutionView from(OperationExecution execution) {
        List<OperationExecutionEvent> events = execution.events();
        UUID latestOperator = events.isEmpty() ? null : events.getLast().operatorId();
        return new OperationExecutionView(execution.id(), execution.tenantId(), "EXEC-" + execution.id(),
                execution.dispatchId(), "DISP-" + execution.dispatchId(), execution.workOrderId(), null, null,
                execution.operationId(), null, null, latestOperator, null, execution.deviceId(), null, null,
                execution.status(), execution.startedAt(), lastEventAt(execution, "PAUSED"),
                lastEventAt(execution, "RESUMED"), lastEventAt(execution, "COMPLETED"), "0", execution.version(),
                actions(execution.status()));
    }

    /** 根据执行状态返回服务端计算的开始、暂停、恢复、完成和报工能力。 */
    private static List<AllowedActionVo> actions(OperationExecutionStatus status) {
        return switch (status) {
            case NotStarted -> List.of(action("start", "mes:execution:manage"));
            case Running -> List.of(action("pause", "mes:execution:manage"),
                    action("complete", "mes:execution:manage"));
            case Paused -> List.of(action("resume", "mes:execution:manage"));
            // 修改用途：报工必须引用已完成的工序执行，和 MES 生产与质检业务规则保持一致。
            case Completed -> List.of(action("report", "mes:report:manage"));
        };
    }

    /** 按权限生成统一动作描述，权限不足时返回可读禁用原因。 */
    private static AllowedActionVo action(String name, String permission) {
        boolean enabled = UserContextHolder.hasPermission(permission);
        return new AllowedActionVo(name, enabled, enabled ? null : "当前用户没有执行该操作的权限");
    }

    /** 读取指定事件类型的最后发生时间；没有事件时返回空。 */
    private static OffsetDateTime lastEventAt(OperationExecution execution, String type) {
        return execution.events().stream()
                .filter(event -> event.type().name().equals(type))
                .map(OperationExecutionEvent::occurredAt)
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
