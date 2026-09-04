package com.ailearn.platform.core.quality.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 质量模块读取的已确认收货事实及其明细。
 */
public record QualityReceiptFact(
        UUID id,
        UUID tenantId,
        UUID purchaseOrderId,
        String purchaseOrderNo,
        OffsetDateTime receiptTime,
        UUID qualityHoldLocationId,
        String status,
        List<QualityReceiptLineFact> lines) {

    /**
     * 固化明细集合，避免调用方修改查询快照。
     */
    public QualityReceiptFact {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
