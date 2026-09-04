package com.ailearn.platform.core.inventory.infrastructure;

import java.math.BigDecimal;

/**
 * 库位库存使用量聚合行，仅承载停用前置检查所需的实物和有效预留数量。
 */
public class InventoryLocationUsageRow {

    private BigDecimal onHandQty;
    private BigDecimal reservedQty;

    public BigDecimal getOnHandQty() {
        return onHandQty;
    }

    public void setOnHandQty(BigDecimal onHandQty) {
        this.onHandQty = onHandQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(BigDecimal reservedQty) {
        this.reservedQty = reservedQty;
    }
}
