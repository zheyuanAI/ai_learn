package com.ailearn.platform.core.quality.dto;

import com.ailearn.platform.core.quality.domain.QualityDispositionFact;
import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 质量处置决定与执行接口视图。
 */
public record QualityDispositionView(
        UUID id,
        String dispositionNo,
        UUID inspectionId,
        UUID purchaseOrderId,
        String purchaseOrderNo,
        UUID purchaseReceiptId,
        UUID purchaseReceiptLineId,
        UUID productId,
        QualityDispositionType dispositionType,
        BigDecimal dispositionQty,
        String reason,
        String status,
        UUID decidedBy,
        OffsetDateTime decidedAt,
        UUID executedBy,
        OffsetDateTime executedAt,
        UUID inventoryTransactionId) {

    /**
     * 从处置事实和质检上下文组装稳定接口视图。
     */
    public static QualityDispositionView of(QualityDispositionFact fact,
                                            QualityInspectionView inspection) {
        return new QualityDispositionView(fact.id(), "QD-" + fact.id(), fact.inspectionId(),
                inspection.purchaseOrderId(), inspection.purchaseOrderNo(), inspection.purchaseReceiptId(),
                inspection.purchaseReceiptLineId(), inspection.productId(), fact.type(), fact.quantity(),
                fact.reason(), fact.status(), fact.decidedBy(), fact.decidedAt(), fact.executedBy(),
                fact.executedAt(), fact.inventoryTransactionId());
    }
}
