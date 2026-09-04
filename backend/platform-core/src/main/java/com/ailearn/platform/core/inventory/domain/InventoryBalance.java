package com.ailearn.platform.core.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 当前库存余额查询快照。
 *
 * @param id 余额行 ID
 * @param tenantId 所属租户
 * @param dimension 库存唯一维度
 * @param onHandQty 实物数量
 * @param reservedQty 有效预留数量
 * @param version 乐观版本号
 * @param lastTransactionAt 最近库存事实时间
 */
public record InventoryBalance(
        UUID id,
        UUID tenantId,
        InventoryDimension dimension,
        BigDecimal onHandQty,
        BigDecimal reservedQty,
        long version,
        OffsetDateTime lastTransactionAt) {

    /**
     * 计算当前可用数量，并再次验证余额不变量。
     *
     * @return {@code onHandQty - reservedQty}
     */
    public BigDecimal availableQty() {
        return InventoryInvariant.availableQty(onHandQty, reservedQty);
    }

    /**
     * 根据库位类型计算可分配数量，QualityHold 始终为零。
     *
     * @param locationType 库位类型
     * @return 可用于预留或领料的数量
     */
    public BigDecimal allocatableQty(LocationType locationType) {
        if (locationType == LocationType.QualityHold) {
            return InventoryInvariant.ZERO;
        }
        return availableQty();
    }
}
