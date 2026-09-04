package com.ailearn.platform.core.purchasing.dto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 到货验收确认请求；实际接收货物统一进入质量隔离位。
 */
public class PurchaseReceiptConfirmRequest {
    private UUID purchaseOrderId;
    private String receiptNo;
    private OffsetDateTime receiptTime;
    private UUID qualityHoldLocationId;
    private List<PurchaseReceiptLineRequest> lines = new ArrayList<>();

    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(UUID purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public OffsetDateTime getReceiptTime() { return receiptTime; }
    public void setReceiptTime(OffsetDateTime receiptTime) { this.receiptTime = receiptTime; }
    public UUID getQualityHoldLocationId() { return qualityHoldLocationId; }
    public void setQualityHoldLocationId(UUID qualityHoldLocationId) { this.qualityHoldLocationId = qualityHoldLocationId; }
    public List<PurchaseReceiptLineRequest> getLines() { return lines; }
    public void setLines(List<PurchaseReceiptLineRequest> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}
