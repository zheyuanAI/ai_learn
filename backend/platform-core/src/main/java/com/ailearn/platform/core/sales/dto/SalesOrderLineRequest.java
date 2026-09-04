package com.ailearn.platform.core.sales.dto;

import java.util.UUID;

/**
 * 销售订单明细请求；数量以字符串传输，避免 HTTP 浮点精度损失。
 */
public class SalesOrderLineRequest {
    private Integer lineNo;
    private UUID productId;
    private String uom;
    private String orderedQty;

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public String getOrderedQty() { return orderedQty; }
    public void setOrderedQty(String orderedQty) { this.orderedQty = orderedQty; }
}
