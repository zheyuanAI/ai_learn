package com.ailearn.platform.core.sales.dto;

import java.util.List;
import java.util.UUID;

/** 退回未发货拣货请求。 */
public class PickTaskReturnRequest {
    private UUID salesOrderId;
    private List<PickTaskReturnLineRequest> lines;

    public UUID getSalesOrderId() { return salesOrderId; }
    public List<PickTaskReturnLineRequest> getLines() { return lines; }
    public void setSalesOrderId(UUID value) { this.salesOrderId = value; }
    public void setLines(List<PickTaskReturnLineRequest> value) { this.lines = value; }
}
