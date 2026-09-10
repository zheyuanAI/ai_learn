package com.ailearn.platform.core.sales.domain;

import java.util.UUID;

/**
 * 销售订单分页查询条件。
 */
public record SalesOrderPageQuery(String keyword, SalesOrderStatus status, UUID customerId,
                                  FulfillmentStatus fulfillmentStatus, int page, int size) {

    public SalesOrderPageQuery {
        page = page < 1 ? 1 : page;
        // 修改：销售订单目录查询与页面下拉选择器统一支持最多 1000 条记录。
        size = size < 1 ? 20 : Math.min(size, 1000);
    }
}
