package com.ailearn.platform.core.inventory.infrastructure;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** PostgreSQL 低库存聚合行。 */
public class LowStockRow {
    private UUID productId;
    private String sku;
    private String productName;
    private String uom;
    private BigDecimal safetyStock;
    private BigDecimal availableQty;
    private BigDecimal shortfallQty;
    private OffsetDateTime lastTransactionAt;

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public BigDecimal getSafetyStock() { return safetyStock; }
    public void setSafetyStock(BigDecimal safetyStock) { this.safetyStock = safetyStock; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getShortfallQty() { return shortfallQty; }
    public void setShortfallQty(BigDecimal shortfallQty) { this.shortfallQty = shortfallQty; }
    public OffsetDateTime getLastTransactionAt() { return lastTransactionAt; }
    public void setLastTransactionAt(OffsetDateTime lastTransactionAt) { this.lastTransactionAt = lastTransactionAt; }
}
