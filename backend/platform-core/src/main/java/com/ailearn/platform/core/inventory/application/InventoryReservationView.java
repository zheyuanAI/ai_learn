package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import java.util.List;

/**
 * 预留及其库位分配的查询视图。
 *
 * @param reservation 预留事实
 * @param allocations 该预留的有效及历史分配
 */
public record InventoryReservationView(
        InventoryReservation reservation,
        List<InventoryReservationAllocation> allocations) {

    /**
     * 创建不可变查询视图，防止调用方修改内核返回集合。
     *
     * @param reservation 预留事实
     * @param allocations 分配集合
     */
    public InventoryReservationView {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }
}
