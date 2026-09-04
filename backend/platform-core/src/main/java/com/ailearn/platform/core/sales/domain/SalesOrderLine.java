package com.ailearn.platform.core.sales.domain;

import com.ailearn.platform.core.sales.exception.SalesOrderErrorCode;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 销售订单明细，保存四类累计数量并在构造时维护单调链约束。
 */
public record SalesOrderLine(UUID id, UUID tenantId, int lineNo, UUID productId, String uom,
                             BigDecimal orderedQty, BigDecimal reservedQty,
                             BigDecimal pickedQty, BigDecimal shippedQty) {

    private static final int SCALE = 6;
    private static final int INTEGER_DIGITS = 13;

    public SalesOrderLine {
        if (id == null || tenantId == null || productId == null || lineNo <= 0
                || uom == null || uom.isBlank()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单明细必要字段不能为空");
        }
        orderedQty = requirePositive("orderedQty", orderedQty);
        reservedQty = requireNonNegative("reservedQty", reservedQty);
        pickedQty = requireNonNegative("pickedQty", pickedQty);
        shippedQty = requireNonNegative("shippedQty", shippedQty);
        if (shippedQty.compareTo(pickedQty) > 0 || pickedQty.compareTo(reservedQty) > 0
                || reservedQty.compareTo(orderedQty) > 0) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                    "销售订单行必须满足 shippedQty <= pickedQty <= reservedQty <= orderedQty");
        }
    }

    /**
     * 计算尚未预留数量。
     *
     * @return orderedQty - reservedQty
     */
    public BigDecimal unreservedQty() {
        return orderedQty.subtract(reservedQty);
    }

    /**
     * 计算已预留但尚未拣货数量。
     *
     * @return reservedQty - pickedQty
     */
    public BigDecimal unpickedQty() {
        return reservedQty.subtract(pickedQty);
    }

    /**
     * 计算已拣货但尚未发货数量。
     *
     * @return pickedQty - shippedQty
     */
    public BigDecimal shippingStagedQty() {
        return pickedQty.subtract(shippedQty);
    }

    /**
     * 计算仍然有效的订单行预留数量。
     *
     * @return reservedQty - shippedQty
     */
    public BigDecimal activeReservedQty() {
        return reservedQty.subtract(shippedQty);
    }

    /**
     * 计算尚未发货数量。
     *
     * @return orderedQty - shippedQty
     */
    public BigDecimal unshippedQty() {
        return orderedQty.subtract(shippedQty);
    }

    /**
     * 生成履约动作后的订单行快照，统一复用构造器中的累计数量不变量校验。
     * 入参：四类累计数量；出参：同一订单行的新快照；流程：不改变订单行身份、产品和计量单位，
     * 仅替换履约累计数量并重新校验 shippedQty <= pickedQty <= reservedQty <= orderedQty。
     *
     * @param reservedQty 新累计预留数量
     * @param pickedQty 新累计拣货数量
     * @param shippedQty 新累计发货数量
     * @return 更新后的订单行
     */
    public SalesOrderLine withFulfillment(BigDecimal reservedQty,
                                          BigDecimal pickedQty,
                                          BigDecimal shippedQty) {
        return new SalesOrderLine(id, tenantId, lineNo, productId, uom,
                orderedQty, reservedQty, pickedQty, shippedQty);
    }

    private static BigDecimal requirePositive(String field, BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 必须大于 0");
        }
        return normalize(field, value);
    }

    private static BigDecimal requireNonNegative(String field, BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 不能为负数");
        }
        return normalize(field, value);
    }

    private static BigDecimal normalize(String field, BigDecimal value) {
        if (value.scale() > SCALE) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 最多支持 6 位小数");
        }
        int integerDigits = Math.max(value.precision() - value.scale(), 0);
        if (integerDigits > INTEGER_DIGITS) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 超出 NUMERIC(19,6) 范围");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
