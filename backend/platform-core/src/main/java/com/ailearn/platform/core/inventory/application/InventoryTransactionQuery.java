package com.ailearn.platform.core.inventory.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 只追加库存流水分页查询条件。
 *
 * @param tenantId 可选租户断言
 * @param transactionType 交易类型
 * @param sourceType 来源单据类型
 * @param sourceId 来源单据 ID
 * @param sourceLineId 来源明细 ID
 * @param productId 产品过滤
 * @param warehouseId 仓库过滤
 * @param locationId 发生位置过滤（来源或目标）
 * @param lotNo 批次过滤
 * @param occurredFrom 业务时间起点
 * @param occurredTo 业务时间终点
 * @param page 1-based 页码
 * @param size 每页条数
 */
public record InventoryTransactionQuery(
        UUID tenantId,
        String transactionType,
        String sourceType,
        UUID sourceId,
        UUID sourceLineId,
        UUID productId,
        UUID warehouseId,
        UUID locationId,
        String lotNo,
        OffsetDateTime occurredFrom,
        OffsetDateTime occurredTo,
        int page,
        int size) {

    /**
     * 创建仅按租户过滤的默认查询。
     *
     * @param tenantId 可选租户断言
     */
    public InventoryTransactionQuery(UUID tenantId) {
        this(tenantId, null, null, null, null, null, null, null, null, null, null, 1, 50);
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
