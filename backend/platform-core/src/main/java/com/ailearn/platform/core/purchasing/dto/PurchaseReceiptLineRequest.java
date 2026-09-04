package com.ailearn.platform.core.purchasing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

/**
 * 到货验收明细请求；不包含质检合格/不合格字段。
 */
public class PurchaseReceiptLineRequest {
    @JsonAlias("lineId")
    private UUID purchaseOrderLineId;
    private UUID productId;
    private String uom;
    private String arrivedQty;
    private String rejectedQty;
    private String receivedQty;
    private String lotNo;
    private String rejectionReason;

    public UUID getPurchaseOrderLineId() { return purchaseOrderLineId; }
    public void setPurchaseOrderLineId(UUID purchaseOrderLineId) { this.purchaseOrderLineId = purchaseOrderLineId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public String getArrivedQty() { return arrivedQty; }
    public void setArrivedQty(String arrivedQty) { this.arrivedQty = arrivedQty; }
    public String getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(String rejectedQty) { this.rejectedQty = rejectedQty; }
    public String getReceivedQty() { return receivedQty; }
    public void setReceivedQty(String receivedQty) { this.receivedQty = receivedQty; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
