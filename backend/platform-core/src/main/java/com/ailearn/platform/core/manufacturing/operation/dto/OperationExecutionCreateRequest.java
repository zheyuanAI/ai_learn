package com.ailearn.platform.core.manufacturing.operation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** 创建工序执行记录请求；工单/工序由派工事实解析，也兼容调用方显式回传用于一致性校验。 */
public record OperationExecutionCreateRequest(
        @JsonProperty("dispatch_order_id") @JsonAlias({"dispatchId", "dispatch_id"}) UUID dispatchId,
        @JsonProperty("work_order_id") @JsonAlias("workOrderId") UUID workOrderId,
        @JsonProperty("operation_id") @JsonAlias("operationId") UUID operationId,
        @JsonProperty("device_id") @JsonAlias("deviceId") UUID deviceId) {

    /** 按一期契约仅使用派工单创建执行实例，工单和工序从派工事实继承。 */
    public OperationExecutionCreateRequest(UUID dispatchId) {
        this(dispatchId, null, null, null);
    }
}
