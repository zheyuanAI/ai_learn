package com.ailearn.platform.core.purchasing.dto;

import java.util.UUID;

/**
 * 采购订单明细请求；数量使用字符串传输，避免 HTTP 浮点精度损失。
 */
public class PurchaseOrderLineRequest {
    private Integer lineNo;
    private UUID productId;
    private String uom;
    private String orderedQty;
    private UUID targetWarehouseId;
    private UUID sourceWorkOrderId;

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public String getOrderedQty() { return orderedQty; }
    public void setOrderedQty(String orderedQty) { this.orderedQty = orderedQty; }
    public UUID getTargetWarehouseId() { return targetWarehouseId; }
    public void setTargetWarehouseId(UUID targetWarehouseId) { this.targetWarehouseId = targetWarehouseId; }
    public UUID getSourceWorkOrderId() { return sourceWorkOrderId; }
    public void setSourceWorkOrderId(UUID sourceWorkOrderId) { this.sourceWorkOrderId = sourceWorkOrderId; }
}
