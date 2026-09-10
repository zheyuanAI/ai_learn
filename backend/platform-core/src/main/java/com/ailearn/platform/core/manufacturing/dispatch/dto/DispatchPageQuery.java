package com.ailearn.platform.core.manufacturing.dispatch.dto;

import java.util.UUID;

/**
 * 派工列表查询参数；所有筛选均在当前可信租户范围内执行，页大小上限为 1000。
 */
public class DispatchPageQuery {
    private int page = 1;
    private int size = 20;
    private UUID workOrderId;
    private UUID operationId;
    private UUID operatorId;
    private String status;

    /** 返回规范化查询参数，避免无界分页和空白状态进入仓储层。 */
    public DispatchPageQuery normalized() {
        DispatchPageQuery result = new DispatchPageQuery();
        result.page = page < 1 ? 1 : page;
        // 修改：工序执行页的派工单选择器统一支持最多 1000 条候选。
        result.size = size < 1 ? 20 : Math.min(size, 1000);
        result.workOrderId = workOrderId;
        result.operationId = operationId;
        result.operatorId = operatorId;
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
    public UUID getOperatorId() { return operatorId; }
    public void setOperatorId(UUID operatorId) { this.operatorId = operatorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
