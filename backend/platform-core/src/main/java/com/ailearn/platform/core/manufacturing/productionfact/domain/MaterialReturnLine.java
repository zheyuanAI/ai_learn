package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** 生产退料明细；退回库位直接作为库存增加目标。 */
public record MaterialReturnLine(UUID id, int lineNo, UUID productId, UUID warehouseId,
                                 UUID locationId, BigDecimal returnQty, UUID inventoryTransactionId) {

    public MaterialReturnLine {
        if (id == null || lineNo <= 0 || productId == null || warehouseId == null || locationId == null
                || returnQty == null || returnQty.signum() <= 0 || returnQty.scale() > 6) {
            throw new IllegalArgumentException("退料明细标识、维度或数量不合法");
        }
    }

    /** 返回确认后绑定库存流水的同一明细。 */
    public MaterialReturnLine confirmed(UUID transactionId) {
        if (transactionId == null) {
            throw new IllegalArgumentException("库存流水不能为空");
        }
        return new MaterialReturnLine(id, lineNo, productId, warehouseId, locationId,
                returnQty, transactionId);
    }
}
