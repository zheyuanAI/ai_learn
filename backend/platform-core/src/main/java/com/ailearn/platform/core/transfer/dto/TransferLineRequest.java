package com.ailearn.platform.core.transfer.dto;

import java.util.UUID;

/**
 * 调拨明细 HTTP 请求；数量使用字符串传输，避免前端浮点精度损失。
 */
public class TransferLineRequest {
    private UUID productId;
    private String lotNo;
    private String uom;
    private String quantity;

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }
}
