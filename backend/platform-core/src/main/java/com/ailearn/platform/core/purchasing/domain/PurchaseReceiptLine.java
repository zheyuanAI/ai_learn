package com.ailearn.platform.core.purchasing.domain;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 到货验收明细；只表达外观验收、拒收和实际接收，不表达质量结论。
 */
public record PurchaseReceiptLine(UUID id, UUID tenantId, UUID purchaseOrderLineId, int lineNo,
                                  UUID productId, String uom, BigDecimal arrivedQty,
                                  BigDecimal rejectedQty, BigDecimal receivedQty, String lotNo,
                                  String rejectionReason) {

    private static final int SCALE = 6;
    private static final int INTEGER_DIGITS = 13;

    /**
     * 校验到货数量关系、拒收原因和批次文本。
     */
    public PurchaseReceiptLine {
        if (id == null || tenantId == null || purchaseOrderLineId == null || productId == null
                || lineNo <= 0 || uom == null || uom.isBlank() || uom.trim().length() > 64) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "到货验收明细必要字段不完整");
        }
        uom = uom.trim();
        lotNo = lotNo == null || lotNo.isBlank() ? "" : lotNo.trim();
        if (lotNo.length() > 128) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "批次号长度不能超过 128 个字符");
        }
        arrivedQty = positive("arrivedQty", arrivedQty);
        rejectedQty = nonNegative("rejectedQty", rejectedQty);
        receivedQty = nonNegative("receivedQty", receivedQty);
        if (arrivedQty.compareTo(rejectedQty.add(receivedQty)) != 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_004,
                    "arrivedQty 必须等于 rejectedQty + receivedQty");
        }
        if (rejectedQty.signum() > 0 && (rejectionReason == null || rejectionReason.trim().isBlank())) {
            throw new PurchasingException(PurchasingErrorCode.PO_005, "拒收数量大于 0 时必须填写拒收原因");
        }
        rejectionReason = rejectionReason == null ? null : rejectionReason.trim();
        if (rejectionReason != null && rejectionReason.length() > 512) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "拒收原因不能超过 512 个字符");
        }
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
