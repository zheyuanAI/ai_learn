package com.ailearn.platform.core.transfer.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code inv_transfer_order_line} 数据库行。
 */
public class TransferLineRow {
    private UUID id;
    private UUID tenantId;
    private UUID transferOrderId;
    private Integer lineNo;
    private UUID productId;
    private String lotNo;
    private String uom;
    private BigDecimal quantity;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getTransferOrderId() { return transferOrderId; }
    public void setTransferOrderId(UUID value) { this.transferOrderId = value; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer value) { this.lineNo = value; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { this.productId = value; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String value) { this.lotNo = value; }
    public String getUom() { return uom; }
    public void setUom(String value) { this.uom = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { this.quantity = value; }
}
