package com.ailearn.platform.core.quality.dto;

import com.ailearn.platform.core.quality.domain.QualityReceiptCandidate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 采购到货质检手动关联选项；返回服务端确认过的收货单和收货行标识，禁止前端拼接或生成标识。
 */
public record QualityReceiptCandidateView(
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

    /**
     * 将质量查询事实转换为前端手动关联选项。
     */
    public static QualityReceiptCandidateView of(QualityReceiptCandidate candidate) {
        return new QualityReceiptCandidateView(candidate.receiptId(), candidate.receiptNo(),
                candidate.purchaseOrderId(), candidate.purchaseOrderNo(), candidate.receiptTime(),
                candidate.receiptLineId(), candidate.purchaseOrderLineId(), candidate.lineNo(),
                candidate.productId(), candidate.uom(), candidate.receivedQty(), candidate.inspectedQty(),
                candidate.remainingQty(), candidate.lotNo());
    }
}
