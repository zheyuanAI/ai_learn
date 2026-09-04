package com.ailearn.platform.core.quality.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 采购质量处置决定及仓库执行事实。
 */
public record QualityDispositionFact(
        UUID id,
        UUID tenantId,
        UUID inspectionId,
        QualityDispositionType type,
        BigDecimal quantity,
        String reason,
        String status,
        UUID decidedBy,
        OffsetDateTime decidedAt,
        UUID executedBy,
        OffsetDateTime executedAt,
        UUID inventoryTransactionId,
        OffsetDateTime createdAt) {
}
