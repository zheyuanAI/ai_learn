package com.ailearn.platform.core.manufacturing.operation.dto;

import java.util.UUID;

/** 工序执行列表查询参数；查询只在当前可信租户内生效，页大小上限为 200。 */
public class OperationExecutionPageQuery {
    private int page = 1;
    private int size = 20;
    private UUID workOrderId;
    private UUID operationId;
    private UUID deviceId;
    private String status;

    /** 规范化分页和状态筛选，防止无界读取。 */
    public OperationExecutionPageQuery normalized() {
        OperationExecutionPageQuery result = new OperationExecutionPageQuery();
        result.page = page < 1 ? 1 : page;
        result.size = size < 1 ? 20 : Math.min(size, 200);
        result.workOrderId = workOrderId;
        result.operationId = operationId;
        result.deviceId = deviceId;
        result.status = status == null || status.isBlank() ? null : status.trim();
        return result;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }
    public UUID getOperationId() { return operationId; }
    public void setOperationId(UUID operationId) { this.operationId = operationId; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
