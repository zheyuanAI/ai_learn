package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Task16 生产事实持久化端口；实现可映射到 V5 的五类事实表。 */
public interface ProductionFactRepository {

    /**
     * 在当前租户范围锁定工单主事实，供退料/成品入库的累计数量校验与确认串行化。
     * <p>内存测试适配器无需额外锁；生产 PostgreSQL 适配器必须使用 {@code FOR UPDATE}。</p>
     */
    default void lockWorkOrder(UUID tenantId, UUID workOrderId) {
        // focused tests 使用内存仓储；生产实现覆盖该方法提供数据库行锁。
    }

    /** 保存领料 Draft。 */
    MaterialIssue saveIssue(MaterialIssue issue);

    /** 按租户读取领料。 */
    Optional<MaterialIssue> findIssue(UUID tenantId, UUID id);

    /** 原子更新领料聚合。 */
    MaterialIssue updateIssue(UUID tenantId, UUID id, UnaryOperator<MaterialIssue> updater);

    /** 保存退料 Draft。 */
    MaterialReturn saveReturn(MaterialReturn value);

    /** 按租户读取退料。 */
    Optional<MaterialReturn> findReturn(UUID tenantId, UUID id);

    /** 原子更新退料聚合。 */
    MaterialReturn updateReturn(UUID tenantId, UUID id, UnaryOperator<MaterialReturn> updater);

    /** 保存不可变报工。 */
    WorkReport saveReport(WorkReport report);

    /** 按租户查询报工。 */
    Optional<WorkReport> findReport(UUID tenantId, UUID id);

    /** 按租户、工单汇总报工。 */
    List<WorkReport> findReports(UUID tenantId, UUID workOrderId);

    /** 保存 Draft 质检。 */
    QualityInspection saveInspection(QualityInspection inspection);

    /** 按租户读取质检。 */
    Optional<QualityInspection> findInspection(UUID tenantId, UUID id);

    /** 原子更新质检聚合。 */
    QualityInspection updateInspection(UUID tenantId, UUID id, UnaryOperator<QualityInspection> updater);

    /** 按租户查询质检。 */
    List<QualityInspection> findInspections(UUID tenantId, UUID workOrderId);

    /** 保存 Draft 成品入库。 */
    FinishedGoodsReceipt saveReceipt(FinishedGoodsReceipt receipt);

    /** 按租户读取成品入库。 */
    Optional<FinishedGoodsReceipt> findReceipt(UUID tenantId, UUID id);

    /** 原子更新成品入库聚合。 */
    FinishedGoodsReceipt updateReceipt(UUID tenantId, UUID id,
                                       UnaryOperator<FinishedGoodsReceipt> updater);

    /** 按租户查询成品入库。 */
    List<FinishedGoodsReceipt> findReceipts(UUID tenantId, UUID workOrderId);

    /** 按租户查询已确认的领料。 */
    List<MaterialIssue> findIssues(UUID tenantId, UUID workOrderId);

    /** 按租户查询已确认的退料。 */
    List<MaterialReturn> findReturns(UUID tenantId, UUID workOrderId);
}
