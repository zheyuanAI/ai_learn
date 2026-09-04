package com.ailearn.platform.core.sales.domain;

import java.util.UUID;

/**
 * 销售订单分页查询条件。
 */
public record SalesOrderPageQuery(String keyword, SalesOrderStatus status, UUID customerId,
                                  FulfillmentStatus fulfillmentStatus, int page, int size) {

    public SalesOrderPageQuery {
        page = page < 1 ? 1 : page;
        size = size < 1 ? 20 : Math.min(size, 200);
    }
}
