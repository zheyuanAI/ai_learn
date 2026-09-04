package com.ailearn.platform.core.transfer.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code inv_transfer_order} 数据库行。
 */
public class TransferOrderRow {
    private UUID id;
    private UUID tenantId;
    private String transferNo;
    private UUID fromWarehouseId;
    private UUID fromLocationId;
    private UUID toWarehouseId;
    private UUID toLocationId;
    private String status;
    private Long version;
    private UUID confirmedBy;
    private OffsetDateTime confirmedAt;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID updatedBy;
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public UUID getFromWarehouseId() { return fromWarehouseId; }
    public void setFromWarehouseId(UUID value) { this.fromWarehouseId = value; }
    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID value) { this.fromLocationId = value; }
    public UUID getToWarehouseId() { return toWarehouseId; }
    public void setToWarehouseId(UUID value) { this.toWarehouseId = value; }
    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID value) { this.toLocationId = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(UUID value) { this.confirmedBy = value; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime value) { this.confirmedAt = value; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID value) { this.createdBy = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID value) { this.updatedBy = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt = value; }
}
