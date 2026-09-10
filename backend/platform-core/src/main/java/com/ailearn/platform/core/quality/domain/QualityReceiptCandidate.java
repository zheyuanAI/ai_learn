package com.ailearn.platform.core.quality.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 采购到货质检可选收货明细；只暴露当前租户中已确认且仍有待检数量的真实事实。
 */
public record QualityReceiptCandidate(
        UUID receiptId,
        String receiptNo,
        UUID purchaseOrderId,
        String purchaseOrderNo,
        OffsetDateTime receiptTime,
        UUID receiptLineId,
        UUID purchaseOrderLineId,
        int lineNo,
        UUID productId,
        String uom,
        BigDecimal receivedQty,
        BigDecimal inspectedQty,
        BigDecimal remainingQty,
        String lotNo) {
}
