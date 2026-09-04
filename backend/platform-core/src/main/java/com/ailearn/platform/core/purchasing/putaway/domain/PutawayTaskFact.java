package com.ailearn.platform.core.purchasing.putaway.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 质量放行后生成的上架任务事实。
 */
public record PutawayTaskFact(
        UUID id,
        UUID tenantId,
        String taskNo,
        UUID purchaseReceiptId,
        UUID purchaseReceiptLineId,
        UUID productId,
        UUID fromLocationId,
        UUID toLocationId,
        UUID warehouseId,
        BigDecimal putawayQty,
        String status,
        UUID confirmedBy,
        OffsetDateTime confirmedAt,
        UUID createdBy,
        OffsetDateTime createdAt,
        UUID inventoryTransactionId) {
}
