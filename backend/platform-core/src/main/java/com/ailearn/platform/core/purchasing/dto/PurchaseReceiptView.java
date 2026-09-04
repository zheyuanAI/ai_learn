package com.ailearn.platform.core.purchasing.dto;

import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceipt;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 到货验收结果；同时返回拒收汇总和库存应用服务产生的流水事实。
 */
public class PurchaseReceiptView {
    private UUID id;
    private String receiptNo;
    private UUID purchaseOrderId;
    private OffsetDateTime receiptTime;
    private UUID qualityHoldLocationId;
    private String status;
    private UUID confirmedBy;
    private String confirmedSessionId;
    private OffsetDateTime confirmedAt;
    private long version;
    private List<PurchaseReceiptLineView> lines;
    private PurchaseArrivalAcceptanceSummary arrivalAcceptanceSummary;
    private PurchaseBalanceDeltaSummary balanceDeltaSummary;
    private List<InventoryTransaction> inventoryTransactions;
    private List<AllowedActionVo> allowedActions;

    public PurchaseReceiptView() {
    }

    /**
     * 将验收聚合、库存流水和服务端汇总组合为响应。
     */
    public PurchaseReceiptView(PurchaseReceipt receipt,
                               PurchaseArrivalAcceptanceSummary arrivalAcceptanceSummary,
                               PurchaseBalanceDeltaSummary balanceDeltaSummary,
                               List<InventoryTransaction> inventoryTransactions,
                               List<AllowedActionVo> allowedActions) {
        this.id = receipt.id();
        this.receiptNo = receipt.receiptNo();
        this.purchaseOrderId = receipt.purchaseOrderId();
        this.receiptTime = receipt.receiptTime();
        this.qualityHoldLocationId = receipt.qualityHoldLocationId();
        this.status = receipt.status().name();
        this.confirmedBy = receipt.confirmedBy();
        this.confirmedSessionId = receipt.confirmedSessionId();
        this.confirmedAt = receipt.confirmedAt();
        this.version = receipt.version();
        this.lines = receipt.lines().stream().map(PurchaseReceiptLineView::from).toList();
        this.arrivalAcceptanceSummary = arrivalAcceptanceSummary;
        this.balanceDeltaSummary = balanceDeltaSummary;
        this.inventoryTransactions = inventoryTransactions == null ? List.of() : List.copyOf(inventoryTransactions);
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public UUID getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public OffsetDateTime getReceiptTime() { return receiptTime; }
    public UUID getQualityHoldLocationId() { return qualityHoldLocationId; }
    public String getStatus() { return status; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public String getConfirmedSessionId() { return confirmedSessionId; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public long getVersion() { return version; }
    public List<PurchaseReceiptLineView> getLines() { return lines; }
    public PurchaseArrivalAcceptanceSummary getArrivalAcceptanceSummary() { return arrivalAcceptanceSummary; }
    public PurchaseBalanceDeltaSummary getBalanceDeltaSummary() { return balanceDeltaSummary; }
    public List<InventoryTransaction> getInventoryTransactions() { return inventoryTransactions; }
    public List<AllowedActionVo> getAllowedActions() { return allowedActions; }

    public void setId(UUID id) { this.id = id; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public void setPurchaseOrderId(UUID purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public void setReceiptTime(OffsetDateTime receiptTime) { this.receiptTime = receiptTime; }
    public void setQualityHoldLocationId(UUID qualityHoldLocationId) { this.qualityHoldLocationId = qualityHoldLocationId; }
    public void setStatus(String status) { this.status = status; }
    public void setConfirmedBy(UUID confirmedBy) { this.confirmedBy = confirmedBy; }
    public void setConfirmedSessionId(String confirmedSessionId) { this.confirmedSessionId = confirmedSessionId; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public void setVersion(long version) { this.version = version; }
    public void setLines(List<PurchaseReceiptLineView> lines) { this.lines = lines; }
    public void setArrivalAcceptanceSummary(PurchaseArrivalAcceptanceSummary value) { this.arrivalAcceptanceSummary = value; }
    public void setBalanceDeltaSummary(PurchaseBalanceDeltaSummary value) { this.balanceDeltaSummary = value; }
    public void setInventoryTransactions(List<InventoryTransaction> value) { this.inventoryTransactions = value; }
    public void setAllowedActions(List<AllowedActionVo> value) { this.allowedActions = value; }
}
