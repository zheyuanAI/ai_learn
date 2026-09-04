package com.ailearn.platform.core.sales.dto;

import java.util.List;

/**
 * 销售订单分页响应。
 */
public record SalesOrderPageResult(List<SalesOrderView> records, long total, int page, int size) {
    public SalesOrderPageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
