package com.ailearn.platform.core.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存业务预留事实。
 *
 * @param id 预留 ID
 * @param tenantId 所属租户
 * @param reservationNo 预留业务编号
 * @param sourceType 来源单据类型
 * @param sourceId 来源单据 ID
 * @param sourceLineId 来源明细 ID
 * @param reservedQty 原始预留数量
 * @param releasedQty 已释放数量
 * @param status Active、PartiallyReleased 或 Released
 * @param version 乐观版本号
 */
public record InventoryReservation(
        UUID id,
        UUID tenantId,
        String reservationNo,
        String sourceType,
        UUID sourceId,
        UUID sourceLineId,
        BigDecimal reservedQty,
        BigDecimal releasedQty,
        String status,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * 计算尚未释放的有效预留。
     *
     * @return 有效预留数量
     */
    public BigDecimal activeQty() {
        return InventoryInvariant.requireBalanced(reservedQty, releasedQty);
    }
}
