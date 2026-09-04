package com.ailearn.platform.core.inventory.infrastructure;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code inv_inventory_transaction} 的 MyBatis 行映射对象。
 */
public class InventoryTransactionRow {
    private UUID id;
    private UUID tenantId;
    private String transactionNo;
    private String transactionType;
    private String sourceType;
    private UUID sourceId;
    private UUID sourceLineId;
    private UUID fromProductId;
    private UUID fromWarehouseId;
    private UUID fromLocationId;
    private String fromLotNo;
    private UUID toProductId;
    private UUID toWarehouseId;
    private UUID toLocationId;
    private String toLotNo;
    private BigDecimal quantity;
    private OffsetDateTime occurredAt;
    private UUID operatorId;
    private String sessionId;
    private String requestId;
    private String idempotencyKey;
    private String payloadDigest;
    private OffsetDateTime createdAt;

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

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getSourceLineId() {
        return sourceLineId;
    }

    public void setSourceLineId(UUID sourceLineId) {
        this.sourceLineId = sourceLineId;
    }

    public UUID getFromProductId() {
        return fromProductId;
    }

    public void setFromProductId(UUID fromProductId) {
        this.fromProductId = fromProductId;
    }

    public UUID getFromWarehouseId() {
        return fromWarehouseId;
    }

    public void setFromWarehouseId(UUID fromWarehouseId) {
        this.fromWarehouseId = fromWarehouseId;
    }

    public UUID getFromLocationId() {
        return fromLocationId;
    }

    public void setFromLocationId(UUID fromLocationId) {
        this.fromLocationId = fromLocationId;
    }

    public String getFromLotNo() {
        return fromLotNo;
    }

    public void setFromLotNo(String fromLotNo) {
        this.fromLotNo = fromLotNo;
    }

    public UUID getToProductId() {
        return toProductId;
    }

    public void setToProductId(UUID toProductId) {
        this.toProductId = toProductId;
    }

    public UUID getToWarehouseId() {
        return toWarehouseId;
    }

    public void setToWarehouseId(UUID toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
    }

    public UUID getToLocationId() {
        return toLocationId;
    }

    public void setToLocationId(UUID toLocationId) {
        this.toLocationId = toLocationId;
    }

    public String getToLotNo() {
        return toLotNo;
    }

    public void setToLotNo(String toLotNo) {
        this.toLotNo = toLotNo;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(UUID operatorId) {
        this.operatorId = operatorId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public void setPayloadDigest(String payloadDigest) {
        this.payloadDigest = payloadDigest;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
