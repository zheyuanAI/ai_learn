package com.ailearn.platform.core.sales.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * 销售订单持久化端口；实现必须按 tenantId 过滤。
 */
public interface SalesOrderRepository {

    /**
     * 创建订单及其明细。
     *
     * @param order 已完成租户和审计初始化的订单
     * @return 已写入订单
     */
    SalesOrder insert(SalesOrder order);

    /**
     * 查询当前租户订单。
     *
     * @param tenantId 可信租户
     * @param id 订单 ID
     * @return 当前租户可见订单
     */
    Optional<SalesOrder> findById(UUID tenantId, UUID id);

    /**
     * 以版本条件更新 Draft 订单字段。
     *
     * @param order 新订单聚合
     * @param expectedVersion 旧版本
     * @return 更新后的订单
     */
    SalesOrder update(SalesOrder order, long expectedVersion);

    /**
     * 以版本条件推进状态或写入完成审计。
     *
     * @param order 新订单聚合
     * @param expectedVersion 旧版本
     * @return 更新后的订单
     */
    SalesOrder updateState(SalesOrder order, long expectedVersion);

    /**
     * 在订单版本条件下原子保存履约订单行和履约事实。
     * 入参：履约后的销售聚合、旧版本和本次只追加事实；出参：已保存聚合；流程：在同一数据库事务中
     * 更新订单表头与明细、追加履约事实，任一行失败整体回滚。
     *
     * @param order 履约后的销售订单
     * @param expectedVersion 读取时版本
     * @param facts 本次履约事实
     * @return 已保存订单
     */
    SalesOrder updateFulfillment(SalesOrder order, long expectedVersion,
                                 java.util.List<SalesFulfillmentFact> facts);

    /**
     * 查询当前租户的订单页。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 订单页
     */
    SalesOrderPage findPage(UUID tenantId, SalesOrderPageQuery query);
}
