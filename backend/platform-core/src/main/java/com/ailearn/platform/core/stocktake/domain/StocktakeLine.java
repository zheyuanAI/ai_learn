package com.ailearn.platform.core.stocktake.domain;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import com.ailearn.platform.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 盘点范围及其系统快照明细。
 *
 * @param id 明细 ID
 * @param tenantId 所属租户
 * @param lineNo 行号
 * @param productId 产品 ID
 * @param warehouseId 仓库 ID
 * @param locationId 库位 ID
 * @param lotNo 批次号
 * @param systemQty 开始盘点时的实物数量
 * @param systemBalanceVersion 开始盘点时的余额版本
 * @param countedQty 实盘数量，可空直到确认
 * @param varianceReason 差异原因
 * @param adjustmentTransactionId 差异调整流水 ID
 */
public record StocktakeLine(UUID id, UUID tenantId, int lineNo, UUID productId,
                            UUID warehouseId, UUID locationId, String lotNo,
                            BigDecimal systemQty, long systemBalanceVersion,
                            BigDecimal countedQty, String varianceReason,
                            UUID adjustmentTransactionId) {

    /**
     * 校验并规范化盘点明细；数量统一遵循库存 NUMERIC(19,6) 规则。
     */
    public StocktakeLine {
        if (id == null || tenantId == null || productId == null || warehouseId == null || locationId == null) {
            throw new ValidationException("盘点明细主键、租户、产品、仓库和库位不能为空");
        }
        if (lineNo < 1) {
            throw new ValidationException("盘点明细行号必须大于 0");
        }
        lotNo = lotNo == null || lotNo.isBlank() ? "" : lotNo.trim();
        if (lotNo.length() > 128) {
            throw new ValidationException("盘点批次号不能超过 128 个字符");
        }
        systemQty = InventoryInvariant.requireNonNegative("systemQty", systemQty);
        if (systemBalanceVersion < 0) {
            throw new ValidationException("盘点系统余额版本不能为负数");
        }
        countedQty = countedQty == null ? null
                : InventoryInvariant.requireNonNegative("countedQty", countedQty);
        if (varianceReason != null) {
            varianceReason = varianceReason.trim();
            if (varianceReason.length() > 512) {
                throw new ValidationException("盘点差异原因不能超过 512 个字符");
            }
        }
    }

    /**
     * 获取该明细对应的库存维度。
     *
     * @return 产品、仓库、库位和批次组成的库存维度
     */
    public InventoryDimension dimension() {
        return new InventoryDimension(productId, warehouseId, locationId, lotNo);
    }

    /**
     * 计算实盘相对系统快照的差异。
     *
     * @return 实盘减系统快照；未录入实盘时返回 null
     */
    public BigDecimal variance() {
        return countedQty == null ? null
                : countedQty.subtract(systemQty).setScale(InventoryInvariant.SCALE);
    }

    /**
     * 写入实盘数量和差异原因，尚不绑定调整流水。
     *
     * @param countedQty 实盘数量
     * @param varianceReason 差异原因
     * @return 带实盘结果的明细
     */
    public StocktakeLine counted(BigDecimal countedQty, String varianceReason) {
        return new StocktakeLine(id, tenantId, lineNo, productId, warehouseId, locationId, lotNo,
                systemQty, systemBalanceVersion, countedQty, varianceReason, null);
    }

    /**
     * 绑定差异调整流水。
     *
     * @param adjustmentTransactionId 调整流水 ID，可为空表示无差异
     * @return 已绑定流水的明细
     */
    public StocktakeLine withAdjustment(UUID adjustmentTransactionId) {
        return new StocktakeLine(id, tenantId, lineNo, productId, warehouseId, locationId, lotNo,
                systemQty, systemBalanceVersion, countedQty, varianceReason, adjustmentTransactionId);
    }
}
