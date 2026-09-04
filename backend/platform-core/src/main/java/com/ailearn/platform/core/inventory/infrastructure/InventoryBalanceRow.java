package com.ailearn.platform.core.inventory.infrastructure;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code inv_inventory_balance} 的 MyBatis 行映射对象，仅供 inventory 基础设施层使用。
 */
public class InventoryBalanceRow {
    private UUID id;
    private UUID tenantId;
    private UUID productId;
    private UUID warehouseId;
    private UUID locationId;
    private String lotNo;
    private BigDecimal onHandQty;
    private BigDecimal reservedQty;
    private Long version;
    private OffsetDateTime lastTransactionAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public BigDecimal getOnHandQty() {
        return onHandQty;
    }

    public void setOnHandQty(BigDecimal onHandQty) {
        this.onHandQty = onHandQty;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(BigDecimal reservedQty) {
        this.reservedQty = reservedQty;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public OffsetDateTime getLastTransactionAt() {
        return lastTransactionAt;
    }

    public void setLastTransactionAt(OffsetDateTime lastTransactionAt) {
        this.lastTransactionAt = lastTransactionAt;
    }
}
