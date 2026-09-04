package com.ailearn.platform.core.manufacturing.execution.domain;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * 工单执行进度快照。
 * <p>
 * 该对象只承载后续执行、报工、质检和成品入库应用服务需要的汇总值，不替代这些领域的事实记录。
 * </p>
 */
public record WorkOrderProgress(Set<UUID> completedOperationIds,
                                BigDecimal reportedQty,
                                BigDecimal qualifiedQty,
                                BigDecimal defectQty,
                                BigDecimal receivedQty,
                                boolean qualityBlocked,
                                boolean pendingInventoryCommands) {

    public WorkOrderProgress {
        if (completedOperationIds == null) {
            throw new IllegalArgumentException("completedOperationIds 不能为空");
        }
        completedOperationIds = Set.copyOf(completedOperationIds);
        requireNonNegative("reportedQty", reportedQty);
        requireNonNegative("qualifiedQty", qualifiedQty);
        requireNonNegative("defectQty", defectQty);
        requireNonNegative("receivedQty", receivedQty);
        if (reportedQty.compareTo(qualifiedQty.add(defectQty)) != 0) {
            throw new IllegalArgumentException("reportedQty 必须等于 qualifiedQty + defectQty");
        }
        if (receivedQty.compareTo(qualifiedQty) > 0) {
            throw new IllegalArgumentException("receivedQty 不能超过 qualifiedQty");
        }
    }

    /** 创建没有现场事实的初始进度。 */
    public static WorkOrderProgress empty() {
        return new WorkOrderProgress(Set.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);
    }

    /** 返回报工数量，便于应用服务和后续查询端口统一使用。 */
    public BigDecimal reportQty() {
        return reportedQty;
    }

    /** 校验数量不为负，避免状态判断建立在无效的累计值上。 */
    private static void requireNonNegative(String name, BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " 不能为负数且不能为空");
        }
    }
}
