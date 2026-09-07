package com.ailearn.platform.core.sales.dto;

import java.util.List;

/**
 * 销售订单分页响应。
 */
public record SalesOrderPageResult(List<SalesOrderView> records, long total, int page, int size) {
    public SalesOrderPageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** 统一分页响应中的总页数。 */
    public long getTotalPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }
}
