package com.ailearn.platform.core.quality.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 采购到货质检事实；事实本身不改变库存。
 */
public record QualityInspectionFact(
        UUID id,
        UUID tenantId,
        UUID purchaseReceiptId,
        UUID purchaseReceiptLineId,
        UUID productId,
        BigDecimal inspectedQty,
        BigDecimal qualifiedQty,
        BigDecimal unqualifiedQty,
        String inspectionNote,
        String status,
        UUID inspectedBy,
        OffsetDateTime inspectedAt,
        OffsetDateTime createdAt) {
}
