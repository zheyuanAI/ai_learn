package com.ailearn.platform.core.sales.dto;

import java.util.List;
import java.util.UUID;

/** 直接拣货确认请求。 */
public class PickTaskConfirmRequest {
    private UUID salesOrderId;
    private List<PickLineRequest> lines;

    public UUID getSalesOrderId() { return salesOrderId; }
    public List<PickLineRequest> getLines() { return lines; }
    public void setSalesOrderId(UUID value) { this.salesOrderId = value; }
    public void setLines(List<PickLineRequest> value) { this.lines = value; }
}
