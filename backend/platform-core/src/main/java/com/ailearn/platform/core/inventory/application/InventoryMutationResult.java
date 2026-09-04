package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 库存命令统一结果。
 * <p>
 * 返回后端计算后的余额、预留、分配、流水和允许动作，供采购、销售、MES 记录事实标识，
 * 调用方不得自行推导库存状态或直接写库存表。
 * </p>
 *
 * @param operation 命令操作名
 * @param quantity 本次命令数量
 * @param balances 受影响余额（普通命令一个，移动命令两个）
 * @param reservation 受影响预留，可空
 * @param allocations 受影响分配，可空
 * @param transactions 本次追加的流水
 * @param allowedActions 当前结果允许的后续动作
 */
public record InventoryMutationResult(
        String operation,
        BigDecimal quantity,
        List<InventoryBalance> balances,
        InventoryReservation reservation,
        List<InventoryReservationAllocation> allocations,
        List<InventoryTransaction> transactions,
        Set<String> allowedActions) {

    /**
     * 规范化结果集合，确保幂等缓存和调用方均不能修改结果。
     */
    public InventoryMutationResult {
        balances = balances == null ? List.of() : List.copyOf(balances);
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
    }
}
