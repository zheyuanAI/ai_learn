package com.ailearn.platform.core.purchasing.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 采购订单创建/草稿修改请求；租户、用户、状态和审计字段由服务端生成。
 */
public class PurchaseOrderSaveRequest {
    private String poNo;
    private UUID supplierId;
    private LocalDate expectedArrivalDate;
    private String remark;
    private Long version;
    private List<PurchaseOrderLineRequest> lines = new ArrayList<>();

    public String getPoNo() { return poNo; }
    public void setPoNo(String poNo) { this.poNo = poNo; }
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public LocalDate getExpectedArrivalDate() { return expectedArrivalDate; }
    public void setExpectedArrivalDate(LocalDate expectedArrivalDate) { this.expectedArrivalDate = expectedArrivalDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<PurchaseOrderLineRequest> getLines() { return lines; }
    public void setLines(List<PurchaseOrderLineRequest> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}
