package com.ailearn.platform.core.manufacturing.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** 创建派工草稿请求；租户和创建人从可信上下文读取。 */
public record DispatchCreateRequest(
        @JsonProperty("work_order_id") @JsonAlias("workOrderId") @NotNull UUID workOrderId,
        @JsonProperty("operation_id") @JsonAlias("operationId") @NotNull UUID operationId,
        @JsonProperty("operator_id") @JsonAlias("operatorId") @NotNull UUID operatorId,
        @JsonProperty("dispatch_qty") @JsonAlias("dispatchQty") @NotNull BigDecimal dispatchQty,
        @JsonProperty("device_id") @JsonAlias("deviceId") UUID deviceId) {

    private static final UUID LEGACY_OPERATOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 兼容内部 focused 测试和旧的进程内调用；占位操作员仅用于保持旧构造器可实例化，HTTP 请求仍必须显式提交操作员和数量。
     *
     * @param workOrderId 工单标识
     * @param operationId 工序标识
     * @param deviceId 可选设备标识
     */
    public DispatchCreateRequest(UUID workOrderId, UUID operationId, UUID deviceId) {
        this(workOrderId, operationId, LEGACY_OPERATOR_ID, BigDecimal.ONE, deviceId);
    }
}
