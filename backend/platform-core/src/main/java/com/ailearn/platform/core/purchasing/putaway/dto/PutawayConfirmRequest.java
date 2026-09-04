package com.ailearn.platform.core.purchasing.putaway.dto;

import java.util.UUID;

/**
 * 上架执行确认请求。
 */
public class PutawayConfirmRequest {

    private UUID taskId;
    private UUID toLocationId;
    private String putawayQty;

    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID value) { this.taskId = value; }
    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID value) { this.toLocationId = value; }
    public String getPutawayQty() { return putawayQty; }
    public void setPutawayQty(String value) { this.putawayQty = value; }
}
