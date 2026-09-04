package com.ailearn.platform.core.inventory.application;

import java.util.UUID;

/**
 * 库存余额分页查询条件。
 *
 * @param tenantId 可选租户断言
 * @param productId 产品过滤
 * @param warehouseId 仓库过滤
 * @param locationId 库位过滤
 * @param lotNo 批次过滤
 * @param page 1-based 页码
 * @param size 每页条数
 */
public record InventoryBalanceQuery(
        UUID tenantId,
        UUID productId,
        UUID warehouseId,
        UUID locationId,
        String lotNo,
        int page,
        int size) {

    /**
     * 创建默认第一页查询。
     *
     * @param tenantId 可选租户断言
     */
    public InventoryBalanceQuery(UUID tenantId) {
        this(tenantId, null, null, null, null, 1, 50);
    }

    /**
     * 返回规范化后的批次过滤，空白批次与无批次统一为 ""。
     *
     * @return 规范化批次或 null（不按批次过滤）
     */
    public String normalizedLotNo() {
        return lotNo == null ? null : (lotNo.isBlank() ? "" : lotNo.trim());
    }
}
