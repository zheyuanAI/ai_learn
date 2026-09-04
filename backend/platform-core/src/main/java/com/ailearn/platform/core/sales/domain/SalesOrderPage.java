package com.ailearn.platform.core.sales.domain;

import java.util.List;

/**
 * 销售订单分页结果。
 */
public record SalesOrderPage(List<SalesOrder> records, long total, int page, int size) {
    public SalesOrderPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
