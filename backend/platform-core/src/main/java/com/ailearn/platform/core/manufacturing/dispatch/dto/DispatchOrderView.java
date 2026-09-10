package com.ailearn.platform.core.manufacturing.dispatch.dto;

import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchStatus;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 派工列表查询视图。
 *
 * 用途：把派工内部事实转换成页面稳定使用的字段，并返回服务端计算的动作能力。
 * 入参：当前租户已过滤的派工事实；出参：派工单号、业务关联字段和 allowedActions。
 * 流程：复制派工事实，按状态和当前用户权限计算可执行动作，避免前端自行猜测。
 */
public record DispatchOrderView(
        UUID id,
        UUID tenantId,
        String dispatchNo,
        UUID workOrderId,
        String workOrderNo,
        String productName,
        UUID operationId,
        Integer operationNo,
        String operationName,
        UUID operatorId,
        String operatorName,
        BigDecimal dispatchQty,
        UUID deviceId,
        String deviceName,
        String deviceCode,
        DispatchStatus status,
        UUID createdBy,
        OffsetDateTime createdAt,
        UUID releasedBy,
        OffsetDateTime releasedAt,
        UUID processingBy,
        OffsetDateTime processingAt,
        UUID completedBy,
        OffsetDateTime completedAt,
        long version,
        List<AllowedActionVo> allowedActions) {

    public DispatchOrderView {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /**
     * 将派工事实转换为列表视图。
     * 入参：同租户派工聚合；出参：页面视图；流程：生成稳定派工号并计算状态动作权限。
     */
    public static DispatchOrderView from(DispatchOrder order) {
        return new DispatchOrderView(order.id(), order.tenantId(), "DISP-" + order.id(),
                order.workOrderId(), null, null, order.operationId(), null, null,
                order.operatorId(), null, order.dispatchQty(), order.deviceId(), null, null,
                order.status(), order.createdBy(), order.createdAt(), order.releasedBy(), order.releasedAt(),
                order.processingBy(), order.processingAt(), order.completedBy(), order.completedAt(),
                order.version(), actions(order.status()));
    }

    /** 根据派工状态和真实权限返回当前页面允许执行的动作。 */
    private static List<AllowedActionVo> actions(DispatchStatus status) {
        return switch (status) {
            case Draft -> List.of(action("release", "mes:dispatch:manage"));
            case Released -> List.of(action("start", "mes:execution:manage"));
            case Processing -> List.of(action("complete", "mes:execution:manage"));
            case Completed -> List.of();
        };
    }

    /** 将权限判断结果转换成统一动作对象，权限不足时保留明确禁用原因。 */
    private static AllowedActionVo action(String name, String permission) {
        boolean enabled = UserContextHolder.hasPermission(permission);
        return new AllowedActionVo(name, enabled, enabled ? null : "当前用户没有执行该操作的权限");
    }
}
