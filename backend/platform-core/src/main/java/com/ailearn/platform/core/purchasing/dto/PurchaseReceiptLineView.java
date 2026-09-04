package com.ailearn.platform.core.purchasing.dto;

import com.ailearn.platform.core.purchasing.domain.PurchaseReceiptLine;
import java.util.UUID;

/**
 * 到货验收明细响应。
 */
public record PurchaseReceiptLineView(UUID id, UUID purchaseOrderLineId, int lineNo,
                                      UUID productId, String uom, String arrivedQty,
                                      String rejectedQty, String receivedQty, String lotNo,
                                      String rejectionReason) {

    /**
     * 将验收明细转为 HTTP 视图。
     */
    public static PurchaseReceiptLineView from(PurchaseReceiptLine line) {
        return new PurchaseReceiptLineView(line.id(), line.purchaseOrderLineId(), line.lineNo(),
                line.productId(), line.uom(), text(line.arrivedQty()), text(line.rejectedQty()),
                text(line.receivedQty()), line.lotNo(), line.rejectionReason());
    }

    private static String text(java.math.BigDecimal value) {
        return value.setScale(6).toPlainString();
    }
}
