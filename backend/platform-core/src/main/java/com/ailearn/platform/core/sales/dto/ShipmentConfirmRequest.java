package com.ailearn.platform.core.sales.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 发货确认请求。 */
public class ShipmentConfirmRequest {
    private UUID salesOrderId;
    private OffsetDateTime shipTime;
    private List<ShipmentLineRequest> shipmentLines;

    public UUID getSalesOrderId() { return salesOrderId; }
    public OffsetDateTime getShipTime() { return shipTime; }
    public List<ShipmentLineRequest> getShipmentLines() { return shipmentLines; }
    public void setSalesOrderId(UUID value) { this.salesOrderId = value; }
    public void setShipTime(OffsetDateTime value) { this.shipTime = value; }
    public void setShipmentLines(List<ShipmentLineRequest> value) { this.shipmentLines = value; }
}
