package com.ailearn.platform.core.purchasing.dto;

import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLine;
import java.util.UUID;

/**
 * 采购订单明细响应，数量统一以六位小数字符串返回。
 */
public record PurchaseOrderLineView(UUID id, int lineNo, UUID productId, String uom,
                                    String orderedQty, String receivedQty, String pendingQty,
                                    UUID targetWarehouseId, UUID sourceWorkOrderId) {

    /**
     * 将领域明细转为 HTTP 视图。
     */
    public static PurchaseOrderLineView from(PurchaseOrderLine line) {
        return new PurchaseOrderLineView(line.id(), line.lineNo(), line.productId(), line.uom(),
                text(line.orderedQty()), text(line.receivedQty()), text(line.pendingQty()),
                line.targetWarehouseId(), line.sourceWorkOrderId());
    }

    private static String text(java.math.BigDecimal value) {
        return value.setScale(6).toPlainString();
    }
}
