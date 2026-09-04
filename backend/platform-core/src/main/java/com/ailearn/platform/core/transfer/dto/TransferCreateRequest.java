package com.ailearn.platform.core.transfer.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 调拨单创建请求，不接收租户、操作人、状态或审计时间字段。
 */
public class TransferCreateRequest {
    private String transferNo;
    private UUID fromWarehouseId;
    private UUID fromLocationId;
    private UUID toWarehouseId;
    private UUID toLocationId;
    private List<TransferLineRequest> lines = new ArrayList<>();

    public String getTransferNo() {
        return transferNo;
    }

    public void setTransferNo(String transferNo) {
        this.transferNo = transferNo;
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

    public List<TransferLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<TransferLineRequest> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}
