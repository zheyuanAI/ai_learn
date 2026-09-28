package com.ailearn.platform.core.purchasing.domain;

import java.math.BigDecimal;

/**
 * 采购订单行的已发生履约累计；收货、质检、放行执行和上架各按自身事实独立聚合。
 * 入参：五类真实非负数量；出参：不可变的订单行只读快照，不参与订单状态机或库存计算。
 */
public record PurchaseOrderLineCumulative(BigDecimal arrivedQty, BigDecimal rejectedQty,
                                          BigDecimal qualifiedQty, BigDecimal releaseExecutedQty,
                                          BigDecimal putawayQty) {
    public static final PurchaseOrderLineCumulative ZERO = new PurchaseOrderLineCumulative(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
}
