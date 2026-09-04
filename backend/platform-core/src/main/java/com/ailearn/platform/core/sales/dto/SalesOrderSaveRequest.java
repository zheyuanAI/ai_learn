package com.ailearn.platform.core.sales.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 销售订单创建/草稿修改请求。
 */
public class SalesOrderSaveRequest {
    private String soNo;
    private UUID customerId;
    private LocalDate plannedShipDate;
    private String remark;
    private List<SalesOrderLineRequest> lines;

    public String getSoNo() { return soNo; }
    public void setSoNo(String soNo) { this.soNo = soNo; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public LocalDate getPlannedShipDate() { return plannedShipDate; }
    public void setPlannedShipDate(LocalDate plannedShipDate) { this.plannedShipDate = plannedShipDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<SalesOrderLineRequest> getLines() { return lines; }
    public void setLines(List<SalesOrderLineRequest> lines) { this.lines = lines; }
}
