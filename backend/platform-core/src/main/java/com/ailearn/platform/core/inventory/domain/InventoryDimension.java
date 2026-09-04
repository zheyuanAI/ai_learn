package com.ailearn.platform.core.inventory.domain;

import com.ailearn.platform.shared.exception.ValidationException;
import java.util.Objects;
import java.util.UUID;

/**
 * 库存余额唯一维度：产品、仓库、库位和可选批次。
 * <p>
 * 租户由命令元数据单独携带并由可信上下文校验；无批次统一规范化为空字符串，
 * 从而与 V2 唯一索引保持一致。
 * </p>
 *
 * @param productId  产品 ID
 * @param warehouseId 仓库 ID
 * @param locationId 库位 ID
 * @param lotNo      批次号，无批次使用空字符串
 */
public record InventoryDimension(
        UUID productId,
        UUID warehouseId,
        UUID locationId,
        String lotNo) {

    /**
     * 校验并规范化库存维度。
     */
    public InventoryDimension {
        if (productId == null || warehouseId == null || locationId == null) {
            throw new ValidationException("库存维度的产品、仓库和库位不能为空");
        }
        lotNo = lotNo == null || lotNo.isBlank() ? "" : lotNo.trim();
        if (lotNo.length() > 128) {
            throw new ValidationException("批次号长度不能超过 128 个字符");
        }
    }

    /**
     * 返回用于数据库锁排序的稳定键，调用方必须用该键对双余额加锁。
     *
     * @param tenantId 租户 ID
     * @return 租户和完整库存维度组成的稳定字符串
     */
    public String lockKey(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId 不能为 null");
        return tenantId + "|" + productId + "|" + warehouseId + "|" + locationId + "|" + lotNo;
    }

    /**
     * 返回已规范化的批次号，供查询、Mapper 和库存流水统一使用。
     * 入参：无；出参：空批次为 {@code ""} 的标准批次号；流程：直接返回构造器校验后的不可变值。
     *
     * @return 规范化批次号
     */
    public String normalizedLotNo() {
        return lotNo;
    }

    /**
     * 创建仅替换库位后的维度，供位置移动和分配移动使用。
     *
     * @param newLocationId 新库位 ID
     * @return 批次、产品和仓库不变的新维度
     */
    public InventoryDimension withLocation(UUID newLocationId) {
        return new InventoryDimension(productId, warehouseId, newLocationId, lotNo);
    }
}
