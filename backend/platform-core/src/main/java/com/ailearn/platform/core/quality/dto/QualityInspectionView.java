package com.ailearn.platform.core.quality.dto;

import com.ailearn.platform.core.quality.domain.QualityInspectionFact;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 质检事实接口视图。
 */
public record QualityInspectionView(
        UUID id,
        String inspectionNo,
        UUID purchaseOrderId,
        String purchaseOrderNo,
        UUID purchaseReceiptId,
        UUID purchaseReceiptLineId,
        UUID productId,
        BigDecimal inspectedQty,
        BigDecimal qualifiedQty,
        BigDecimal unqualifiedQty,
        String unqualifiedReason,
        UUID inspectedBy,
        OffsetDateTime inspectedAt,
        String status) {

    /**
     * 从质量事实和收货上下文组装稳定接口视图。
     */
    public static QualityInspectionView of(QualityInspectionFact fact, String orderNo, UUID orderId) {
        return new QualityInspectionView(fact.id(), "QI-" + fact.id(), orderId, orderNo,
                fact.purchaseReceiptId(), fact.purchaseReceiptLineId(), fact.productId(), fact.inspectedQty(),
                fact.qualifiedQty(), fact.unqualifiedQty(), fact.inspectionNote(), fact.inspectedBy(),
                fact.inspectedAt(), fact.status());
    }
}
