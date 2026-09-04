package com.ailearn.platform.core.sales.dto;

import java.util.UUID;

/** 发货明细。 */
public class ShipmentLineRequest {
    private UUID salesOrderLineId;
    private UUID productId;
    private String shipQty;

    public UUID getSalesOrderLineId() { return salesOrderLineId; }
    public UUID getProductId() { return productId; }
    public String getShipQty() { return shipQty; }
    public void setSalesOrderLineId(UUID value) { this.salesOrderLineId = value; }
    public void setProductId(UUID value) { this.productId = value; }
    public void setShipQty(String value) { this.shipQty = value; }
}
