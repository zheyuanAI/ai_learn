package com.ailearn.platform.core.sales.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 销售履约只追加事实；库存流水仍由 InventoryCommandService 产生，本事实只保存业务动作和关联标识。
 */
public record SalesFulfillmentFact(
        UUID id,
        UUID tenantId,
        UUID salesOrderId,
        UUID salesOrderLineId,
        String actionType,
        UUID operationId,
        BigDecimal quantity,
        UUID fromLocationId,
        UUID toLocationId,
        UUID reservationId,
        UUID allocationId,
        String idempotencyKey,
        UUID userId,
        String sessionId,
        String requestId,
        OffsetDateTime occurredAt) {
}
