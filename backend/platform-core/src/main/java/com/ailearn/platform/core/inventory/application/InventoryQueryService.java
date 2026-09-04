package com.ailearn.platform.core.inventory.application;

/**
 * 库存事实查询应用端口。
 * <p>
 * 查询实现统一按可信租户过滤，返回余额公式、预留分配和只追加流水；其他领域不得跨模块直查库存表。
 * </p>
 */
public interface InventoryQueryService {

    /**
     * 查询当前库存余额。
     *
     * @param query 产品、仓库、库位、批次和分页条件
     * @return 租户隔离的余额分页
     */
    InventoryBalancePage queryBalances(InventoryBalanceQuery query);

    /**
     * 查询预留及其库位分配。
     *
     * @param query 来源单据、预留状态、维度和分页条件
     * @return 租户隔离的预留分页
     */
    InventoryReservationPage queryReservations(InventoryReservationQuery query);

    /**
     * 查询只追加库存流水。
     *
     * @param query 交易、来源、维度、时间和分页条件
     * @return 租户隔离的流水分页
     */
    InventoryTransactionPage queryTransactions(InventoryTransactionQuery query);
}
