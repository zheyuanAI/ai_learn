package com.ailearn.platform.core.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 预留在具体库存维度上的分配事实。
 *
 * @param id 分配 ID
 * @param tenantId 所属租户
 * @param reservationId 预留 ID
 * @param dimension 分配所在库存维度
 * @param allocatedQty 分配数量
 * @param releasedQty 已从该分配释放的数量
 * @param version 乐观版本号
 */
public record InventoryReservationAllocation(
        UUID id,
        UUID tenantId,
        UUID reservationId,
        InventoryDimension dimension,
        BigDecimal allocatedQty,
        BigDecimal releasedQty,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * 计算该库位分配仍然有效的数量。
     *
     * @return 有效分配数量
     */
    public BigDecimal activeQty() {
        return InventoryInvariant.requireBalanced(allocatedQty, releasedQty);
    }
}
