package com.ailearn.platform.core.purchasing.domain;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 采购订单明细，保存订单数量和累计实际接收数量。
 */
public record PurchaseOrderLine(UUID id, UUID tenantId, int lineNo, UUID productId, String uom,
                                BigDecimal orderedQty, BigDecimal receivedQty,
                                UUID targetWarehouseId, UUID sourceWorkOrderId) {

    private static final int SCALE = 6;
    private static final int INTEGER_DIGITS = 13;

    /**
     * 校验采购明细身份、数量精度和累计接收上限。
     */
    public PurchaseOrderLine {
        if (id == null || tenantId == null || productId == null || targetWarehouseId == null
                || lineNo <= 0 || uom == null || uom.isBlank() || uom.trim().length() > 64) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "采购订单明细必要字段不完整");
        }
        uom = uom.trim();
        orderedQty = positive("orderedQty", orderedQty);
        receivedQty = nonNegative("receivedQty", receivedQty);
        if (receivedQty.compareTo(orderedQty) > 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_002, "累计实际接收数量超过订单数量");
        }
    }

    /**
     * 计算当前待收数量；拒收数量不消耗待收数量。
     *
     * @return 订单数量减累计实际接收数量
     */
    public BigDecimal pendingQty() {
        return orderedQty.subtract(receivedQty).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /**
     * 应用一次实际接收数量，拒收数量不会进入本方法。
     *
     * @param delta 本次实际接收数量
     * @return 更新后的明细
     */
    public PurchaseOrderLine received(BigDecimal delta) {
        BigDecimal normalized = nonNegative("receivedDelta", delta);
        return new PurchaseOrderLine(id, tenantId, lineNo, productId, uom, orderedQty,
                receivedQty.add(normalized), targetWarehouseId, sourceWorkOrderId);
    }

    private static BigDecimal positive(String field, BigDecimal value) {
        BigDecimal normalized = normalize(field, value);
        if (normalized.signum() <= 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 必须大于 0");
        }
        return normalized;
    }

    private static BigDecimal nonNegative(String field, BigDecimal value) {
        BigDecimal normalized = normalize(field, value);
        if (normalized.signum() < 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 不能为负数");
        }
        return normalized;
    }

    private static BigDecimal normalize(String field, BigDecimal value) {
        if (value == null || value.scale() > SCALE) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 数量精度不合法");
        }
        int integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (integerDigits > INTEGER_DIGITS) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 超出 NUMERIC(19,6) 范围");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
