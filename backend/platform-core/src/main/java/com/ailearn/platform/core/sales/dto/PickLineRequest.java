package com.ailearn.platform.core.sales.dto;

import java.util.UUID;

/** 直接拣货明细；数量使用字符串，库位由服务端校验并解析所属仓库。 */
public class PickLineRequest {
    private UUID salesOrderLineId;
    private String pickedQty;
    private UUID sourceLocationId;
    private UUID shippingLocationId;

    public UUID getSalesOrderLineId() { return salesOrderLineId; }
    public String getPickedQty() { return pickedQty; }
    public UUID getSourceLocationId() { return sourceLocationId; }
    public UUID getShippingLocationId() { return shippingLocationId; }
    public void setSalesOrderLineId(UUID value) { this.salesOrderLineId = value; }
    public void setPickedQty(String value) { this.pickedQty = value; }
    public void setSourceLocationId(UUID value) { this.sourceLocationId = value; }
    public void setShippingLocationId(UUID value) { this.shippingLocationId = value; }
}
