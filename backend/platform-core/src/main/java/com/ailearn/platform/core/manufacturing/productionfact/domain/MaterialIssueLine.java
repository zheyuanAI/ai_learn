package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** 生产领料明细；数量和库存维度与 V5 表结构保持一致。 */
public record MaterialIssueLine(UUID id, int lineNo, UUID productId, UUID warehouseId,
                                UUID locationId, BigDecimal issueQty, UUID inventoryTransactionId) {

    public MaterialIssueLine {
        requireIds(productId, warehouseId, locationId);
        if (id == null || lineNo <= 0 || issueQty == null || issueQty.signum() <= 0
                || issueQty.scale() > 6) {
            throw new IllegalArgumentException("领料明细标识、行号或数量不合法");
        }
    }

    /** 返回确认后绑定库存流水的同一明细。 */
    public MaterialIssueLine confirmed(UUID transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("库存流水不能为空");
        }
        return new MaterialIssueLine(id, lineNo, productId, warehouseId, locationId,
                issueQty, transactionId);
    }

    private static void requireIds(UUID productId, UUID warehouseId, UUID locationId) {
        if (productId == null || warehouseId == null || locationId == null) {
            throw new IllegalArgumentException("领料产品、仓库和库位不能为空");
        }
    }
}
