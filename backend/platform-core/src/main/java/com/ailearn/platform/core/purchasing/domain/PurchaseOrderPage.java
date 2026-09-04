package com.ailearn.platform.core.purchasing.domain;

import java.util.List;

/**
 * 租户隔离的采购订单分页结果。
 */
public record PurchaseOrderPage(List<PurchaseOrder> records, long total, int page, int size) {

    /**
     * 规范化分页集合。
     */
    public PurchaseOrderPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
