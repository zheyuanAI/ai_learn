package com.ailearn.platform.core.sales.dto;

import java.util.List;
import java.util.UUID;

/** 销售履约命令结果；摘要字段保留库存命令返回的业务标识，便于页面和追溯查询。 */
public record SalesFulfillmentResult(
        String action,
        UUID operationId,
        SalesOrderView order,
        List<UUID> inventoryTransactionIds,
        List<UUID> reservationIds) {

    public SalesFulfillmentResult {
        inventoryTransactionIds = inventoryTransactionIds == null ? List.of() : List.copyOf(inventoryTransactionIds);
        reservationIds = reservationIds == null ? List.of() : List.copyOf(reservationIds);
    }
}
