package com.ailearn.platform.core.inventory.application;

import java.util.UUID;

/**
 * 库存预留及分配分页查询条件。
 *
 * @param tenantId 可选租户断言
 * @param reservationId 预留 ID
 * @param sourceType 来源单据类型
 * @param sourceId 来源单据 ID
 * @param sourceLineId 来源明细 ID
 * @param status 预留状态
 * @param productId 分配产品过滤
 * @param warehouseId 分配仓库过滤
 * @param locationId 分配库位过滤
 * @param lotNo 分配批次过滤
 * @param page 1-based 页码
 * @param size 每页条数
 */
public record InventoryReservationQuery(
        UUID tenantId,
        UUID reservationId,
        String sourceType,
        UUID sourceId,
        UUID sourceLineId,
        String status,
        UUID productId,
        UUID warehouseId,
        UUID locationId,
        String lotNo,
        int page,
        int size) {

    /**
     * 创建仅按租户过滤的默认查询。
     *
     * @param tenantId 可选租户断言
     */
    public InventoryReservationQuery(UUID tenantId) {
        this(tenantId, null, null, null, null, null, null, null, null, null, 1, 50);
    }

    /**
     * 返回规范化后的批次过滤。
     *
     * @return 规范化批次或 null
     */
    public String normalizedLotNo() {
        return lotNo == null ? null : (lotNo.isBlank() ? "" : lotNo.trim());
    }
}
