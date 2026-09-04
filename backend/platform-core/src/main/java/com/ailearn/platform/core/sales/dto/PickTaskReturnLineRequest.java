package com.ailearn.platform.core.sales.dto;

import java.util.UUID;

/** 退回未发货拣货明细。 */
public class PickTaskReturnLineRequest {
    private UUID salesOrderLineId;
    private String returnQty;
    private UUID toLocationId;

    public UUID getSalesOrderLineId() { return salesOrderLineId; }
    public String getReturnQty() { return returnQty; }
    public UUID getToLocationId() { return toLocationId; }
    public void setSalesOrderLineId(UUID value) { this.salesOrderLineId = value; }
    public void setReturnQty(String value) { this.returnQty = value; }
    public void setToLocationId(UUID value) { this.toLocationId = value; }
}
