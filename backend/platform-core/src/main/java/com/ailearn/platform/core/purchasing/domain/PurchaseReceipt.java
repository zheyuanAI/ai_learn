package com.ailearn.platform.core.purchasing.domain;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 到货验收事实聚合；确认时记录拒收审计，实际接收数量由应用服务桥接库存内核。
 */
public record PurchaseReceipt(UUID id, UUID tenantId, String receiptNo, UUID purchaseOrderId,
                              OffsetDateTime receiptTime, UUID qualityHoldLocationId,
                              PurchaseReceiptStatus status, UUID confirmedBy,
                              String confirmedSessionId, OffsetDateTime confirmedAt,
                              long version, UUID createdBy, OffsetDateTime createdAt,
                              UUID updatedBy, OffsetDateTime updatedAt,
                              List<PurchaseReceiptLine> lines) {

    /**
     * 校验验收单和明细租户边界。
     */
    public PurchaseReceipt {
        if (id == null || tenantId == null || receiptNo == null || receiptNo.isBlank()
                || receiptNo.trim().length() > 64 || purchaseOrderId == null || receiptTime == null
                || qualityHoldLocationId == null || status == null || createdBy == null || version < 0
                || lines == null || lines.isEmpty()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "到货验收单必要字段不完整");
        }
        receiptNo = receiptNo.trim();
        lines = List.copyOf(lines);
        Set<UUID> orderLineIds = new HashSet<>();
        for (PurchaseReceiptLine line : lines) {
            if (line == null || !tenantId.equals(line.tenantId()) || !orderLineIds.add(line.purchaseOrderLineId())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "到货验收明细必须同租户且订单行唯一");
            }
        }
        if (status == PurchaseReceiptStatus.Confirmed
                && (confirmedBy == null || confirmedSessionId == null || confirmedSessionId.isBlank()
                || confirmedAt == null)) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "已确认收货必须记录可信操作审计");
        }
    }
}
