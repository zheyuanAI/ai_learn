package com.ailearn.platform.core.purchasing.dto;

import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 采购订单详情，返回生命周期、累计收货和服务端允许动作。
 */
public class PurchaseOrderView {
    private UUID id;
    private String poNo;
    private UUID supplierId;
    private LocalDate expectedArrivalDate;
    private String status;
    private String completionType;
    private String completionReason;
    private UUID completedBy;
    private String completedSessionId;
    private OffsetDateTime completedAt;
    private String remark;
    private long version;
    private List<PurchaseOrderLineView> lines;
    private List<AllowedActionVo> allowedActions;

    public PurchaseOrderView() {
    }

    /**
     * 按采购聚合构造响应，动作由应用服务根据状态生成。
     */
    public PurchaseOrderView(PurchaseOrder order, List<AllowedActionVo> allowedActions) {
        this.id = order.id();
        this.poNo = order.poNo();
        this.supplierId = order.supplierId();
        this.expectedArrivalDate = order.expectedArrivalDate();
        this.status = order.status().name();
        this.completionType = order.completionType() == null ? null : order.completionType().name();
        this.completionReason = order.completionReason();
        this.completedBy = order.completedBy();
        this.completedSessionId = order.completedSessionId();
        this.completedAt = order.completedAt();
        this.remark = order.remark();
        this.version = order.version();
        this.lines = order.lines().stream().map(PurchaseOrderLineView::from).toList();
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public UUID getId() { return id; }
    public String getPoNo() { return poNo; }
    public UUID getSupplierId() { return supplierId; }
    public LocalDate getExpectedArrivalDate() { return expectedArrivalDate; }
    public String getStatus() { return status; }
    public String getCompletionType() { return completionType; }
    public String getCompletionReason() { return completionReason; }
    public UUID getCompletedBy() { return completedBy; }
    public String getCompletedSessionId() { return completedSessionId; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getRemark() { return remark; }
    public long getVersion() { return version; }
    public List<PurchaseOrderLineView> getLines() { return lines; }
    public List<AllowedActionVo> getAllowedActions() { return allowedActions; }

    public void setId(UUID id) { this.id = id; }
    public void setPoNo(String poNo) { this.poNo = poNo; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public void setExpectedArrivalDate(LocalDate expectedArrivalDate) { this.expectedArrivalDate = expectedArrivalDate; }
    public void setStatus(String status) { this.status = status; }
    public void setCompletionType(String completionType) { this.completionType = completionType; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }
    public void setCompletedBy(UUID completedBy) { this.completedBy = completedBy; }
    public void setCompletedSessionId(String completedSessionId) { this.completedSessionId = completedSessionId; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setVersion(long version) { this.version = version; }
    public void setLines(List<PurchaseOrderLineView> lines) { this.lines = lines; }
    public void setAllowedActions(List<AllowedActionVo> allowedActions) { this.allowedActions = allowedActions; }
}
