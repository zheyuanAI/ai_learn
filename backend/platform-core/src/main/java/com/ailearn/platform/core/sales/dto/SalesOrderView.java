package com.ailearn.platform.core.sales.dto;

import com.ailearn.platform.core.masterdata.domain.entity.Customer;
import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 销售订单详情响应；履约状态明确标记为服务端派生值。
 */
public class SalesOrderView {
    private UUID id;
    private String soNo;
    private UUID customerId;
    private String customerCode;
    private String customerName;
    private LocalDate plannedShipDate;
    private String status;
    private String fulfillmentStatus;
    private String completionType;
    private String completionReason;
    private UUID completedBy;
    private String completedSessionId;
    private OffsetDateTime completedAt;
    private String remark;
    private long version;
    private List<SalesOrderLineView> lines;
    private List<AllowedActionVo> allowedActions;

    public SalesOrderView() {
    }

    /**
     * 按订单聚合计算完整详情和允许动作。
     *
     * @param order 销售订单聚合
     * @param allowedActions 服务端按状态计算的动作
     */
    public SalesOrderView(SalesOrder order, List<AllowedActionVo> allowedActions) {
        this(order, allowedActions, null, Map.of());
    }

    /**
     * 按订单聚合计算详情，并补充当前租户内客户和物料主数据展示字段。
     *
     * @param order 销售订单聚合
     * @param allowedActions 服务端按状态计算的动作
     * @param customer 当前租户客户主数据，可为空
     * @param products 按产品 ID 索引的当前租户物料主数据
     */
    public SalesOrderView(SalesOrder order, List<AllowedActionVo> allowedActions,
                          Customer customer, Map<UUID, Product> products) {
        this.id = order.id();
        this.soNo = order.soNo();
        this.customerId = order.customerId();
        this.customerCode = customer == null ? null : customer.getCustomerCode();
        this.customerName = customer == null ? null : customer.getCustomerName();
        this.plannedShipDate = order.plannedShipDate();
        this.status = order.status().name();
        this.fulfillmentStatus = order.fulfillmentStatus().name();
        this.completionType = order.completionType() == null ? null : order.completionType().name();
        this.completionReason = order.completionReason();
        this.completedBy = order.completedBy();
        this.completedSessionId = order.completedSessionId();
        this.completedAt = order.completedAt();
        this.remark = order.remark();
        this.version = order.version();
        Map<UUID, Product> productIndex = products == null ? Map.of() : products;
        this.lines = order.lines().stream()
                .map(line -> SalesOrderLineView.from(line, productIndex.get(line.productId())))
                .toList();
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public UUID getId() { return id; }
    public String getSoNo() { return soNo; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerName() { return customerName; }
    public LocalDate getPlannedShipDate() { return plannedShipDate; }
    public String getStatus() { return status; }
    public String getFulfillmentStatus() { return fulfillmentStatus; }
    public String getCompletionType() { return completionType; }
    public String getCompletionReason() { return completionReason; }
    public UUID getCompletedBy() { return completedBy; }
    public String getCompletedSessionId() { return completedSessionId; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public String getRemark() { return remark; }
    public long getVersion() { return version; }
    public List<SalesOrderLineView> getLines() { return lines; }
    public List<AllowedActionVo> getAllowedActions() { return allowedActions; }

    public void setId(UUID id) { this.id = id; }
    public void setSoNo(String soNo) { this.soNo = soNo; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setPlannedShipDate(LocalDate plannedShipDate) { this.plannedShipDate = plannedShipDate; }
    public void setStatus(String status) { this.status = status; }
    public void setFulfillmentStatus(String fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public void setCompletionType(String completionType) { this.completionType = completionType; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }
    public void setCompletedBy(UUID completedBy) { this.completedBy = completedBy; }
    public void setCompletedSessionId(String completedSessionId) { this.completedSessionId = completedSessionId; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public void setRemark(String remark) { this.remark = remark; }
    public void setVersion(long version) { this.version = version; }
    public void setLines(List<SalesOrderLineView> lines) { this.lines = lines; }
    public void setAllowedActions(List<AllowedActionVo> allowedActions) { this.allowedActions = allowedActions; }
}
