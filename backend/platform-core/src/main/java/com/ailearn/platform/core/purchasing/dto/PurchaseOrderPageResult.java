package com.ailearn.platform.core.purchasing.dto;

import java.util.List;

/**
 * 采购订单分页响应。
 */
public record PurchaseOrderPageResult(List<PurchaseOrderView> records, long total, int page, int size) {

    public PurchaseOrderPageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
