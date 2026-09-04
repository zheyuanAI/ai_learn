package com.ailearn.platform.core.manufacturing.foundation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 工单创建命令；来源销售订单行可空，且不允许客户端指定 tenantId/createdBy。 */
public record WorkOrderCreateRequest(String workOrderNo, UUID productId, BigDecimal plannedQty,
                                     OffsetDateTime plannedStartTime, OffsetDateTime plannedFinishTime,
                                     UUID bomId, UUID routingId, UUID sourceSalesOrderLineId) {
}
