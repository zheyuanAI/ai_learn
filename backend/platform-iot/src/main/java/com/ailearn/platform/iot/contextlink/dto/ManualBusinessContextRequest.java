package com.ailearn.platform.iot.contextlink.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** 告警人工业务上下文请求；至少提供一个同租户的工序执行或工单标识。 */
public record ManualBusinessContextRequest(
        @JsonProperty("operation_execution_id") @JsonAlias("operationExecutionId") UUID operationExecutionId,
        @JsonProperty("work_order_id") @JsonAlias("workOrderId") UUID workOrderId) {
}
