package com.ailearn.platform.core.stocktake.infrastructure;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code inv_stocktake_order_line} 数据库行。
 */
public class StocktakeLineRow {

    private UUID id;
    private UUID tenantId;
    private UUID stocktakeOrderId;
    private Integer lineNo;
    private UUID productId;
    private UUID warehouseId;
    private UUID locationId;
    private String lotNo;
    private BigDecimal systemQty;
    private Long systemBalanceVersion;
    private BigDecimal countedQty;
    private String varianceReason;
    private UUID adjustmentTransactionId;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getStocktakeOrderId() { return stocktakeOrderId; }
    public void setStocktakeOrderId(UUID stocktakeOrderId) { this.stocktakeOrderId = stocktakeOrderId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public BigDecimal getSystemQty() { return systemQty; }
    public void setSystemQty(BigDecimal systemQty) { this.systemQty = systemQty; }
    public Long getSystemBalanceVersion() { return systemBalanceVersion; }
    public void setSystemBalanceVersion(Long systemBalanceVersion) { this.systemBalanceVersion = systemBalanceVersion; }
    public BigDecimal getCountedQty() { return countedQty; }
    public void setCountedQty(BigDecimal countedQty) { this.countedQty = countedQty; }
    public String getVarianceReason() { return varianceReason; }
    public void setVarianceReason(String varianceReason) { this.varianceReason = varianceReason; }
    public UUID getAdjustmentTransactionId() { return adjustmentTransactionId; }
    public void setAdjustmentTransactionId(UUID adjustmentTransactionId) { this.adjustmentTransactionId = adjustmentTransactionId; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
