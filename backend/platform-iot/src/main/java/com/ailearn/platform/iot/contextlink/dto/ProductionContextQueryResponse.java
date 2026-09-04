package com.ailearn.platform.iot.contextlink.dto;

import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Core ProductionContextQuery 的线协议响应；不把 Core 类型引入 IoT 编译依赖。 */
public record ProductionContextQueryResponse(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("work_order_id") UUID workOrderId,
        @JsonProperty("operation_execution_id") UUID operationExecutionId,
        @JsonProperty("operation_id") UUID operationId,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("event_at") OffsetDateTime eventAt) {

    /** 将已解码的线协议响应转换为 IoT 自有 DTO，并复用字段完整性校验。 */
    public ProductionContextView toView() {
        return new ProductionContextView(tenantId, deviceId, workOrderId, operationExecutionId,
                operationId, startedAt, eventAt);
    }
}
