package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 成品入库聚合根，对应 V5 mes_finished_goods_receipt；产品由工单事实解析。 */
public record FinishedGoodsReceipt(UUID id, UUID tenantId, String receiptNo, UUID workOrderId,
                                   BigDecimal receiptQty, UUID warehouseId, UUID locationId,
                                   FinishedGoodsReceiptStatus status, UUID inventoryOperationId,
                                   UUID inventoryTransactionId, UUID confirmedBy,
                                   String confirmedSessionId, OffsetDateTime confirmedAt,
                                   UUID createdBy, OffsetDateTime createdAt, UUID updatedBy,
                                   OffsetDateTime updatedAt) {

    public FinishedGoodsReceipt {
        if (tenantId == null || id == null || receiptNo == null || receiptNo.isBlank()
                || receiptNo.length() > 64 || workOrderId == null || receiptQty == null
                || receiptQty.signum() <= 0 || receiptQty.scale() > 6 || warehouseId == null
                || locationId == null || status == null || createdBy == null || createdAt == null) {
            throw new IllegalArgumentException("成品入库字段或数量不合法");
        }
        if (status == FinishedGoodsReceiptStatus.Confirmed
                && (inventoryOperationId == null || inventoryTransactionId == null || confirmedBy == null
                || confirmedSessionId == null || confirmedSessionId.isBlank() || confirmedAt == null)) {
            throw new IllegalArgumentException("已确认成品入库必须保留库存和审计事实");
        }
    }

    /** 创建 Draft 成品入库单。 */
    public static FinishedGoodsReceipt draft(UUID id, UUID tenantId, String receiptNo, UUID workOrderId,
                                             BigDecimal receiptQty, UUID warehouseId, UUID locationId,
                                             UUID userId, OffsetDateTime now) {
        return new FinishedGoodsReceipt(id, tenantId, receiptNo, workOrderId, receiptQty, warehouseId,
                locationId, FinishedGoodsReceiptStatus.Draft, null, null, null, null, null,
                userId, now, userId, now);
    }

    /** 生成确认后的成品入库事实。 */
    public FinishedGoodsReceipt confirmed(UUID operationId, UUID transactionId, UUID userId,
                                          String sessionId, OffsetDateTime now) {
        return new FinishedGoodsReceipt(id, tenantId, receiptNo, workOrderId, receiptQty, warehouseId,
                locationId, FinishedGoodsReceiptStatus.Confirmed, operationId, transactionId,
                userId, sessionId, now, createdBy, createdAt, userId, now);
    }
}
