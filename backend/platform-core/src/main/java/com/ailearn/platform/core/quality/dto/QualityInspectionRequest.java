package com.ailearn.platform.core.quality.dto;

/**
 * 采购到货质检请求。数量使用字符串，避免 JSON 浮点数损失 NUMERIC(19,6) 精度。
 */
public class QualityInspectionRequest {

    private java.util.UUID purchaseOrderId;
    private java.util.UUID purchaseReceiptId;
    private java.util.UUID purchaseReceiptLineId;
    private java.util.UUID productId;
    private String inspectedQty;
    private String qualifiedQty;
    private String unqualifiedQty;
    private String unqualifiedReason;
    private String inspectionRemark;

    public java.util.UUID getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(java.util.UUID value) { this.purchaseOrderId = value; }
    public java.util.UUID getPurchaseReceiptId() { return purchaseReceiptId; }
    public void setPurchaseReceiptId(java.util.UUID value) { this.purchaseReceiptId = value; }
    public java.util.UUID getPurchaseReceiptLineId() { return purchaseReceiptLineId; }
    public void setPurchaseReceiptLineId(java.util.UUID value) { this.purchaseReceiptLineId = value; }
    public java.util.UUID getProductId() { return productId; }
    public void setProductId(java.util.UUID value) { this.productId = value; }
    public String getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(String value) { this.inspectedQty = value; }
    public String getQualifiedQty() { return qualifiedQty; }
    public void setQualifiedQty(String value) { this.qualifiedQty = value; }
    public String getUnqualifiedQty() { return unqualifiedQty; }
    public void setUnqualifiedQty(String value) { this.unqualifiedQty = value; }
    public String getUnqualifiedReason() { return unqualifiedReason; }
    public void setUnqualifiedReason(String value) { this.unqualifiedReason = value; }
    public String getInspectionRemark() { return inspectionRemark; }
    public void setInspectionRemark(String value) { this.inspectionRemark = value; }
}
