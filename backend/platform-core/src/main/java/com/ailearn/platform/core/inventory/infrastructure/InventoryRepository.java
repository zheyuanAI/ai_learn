package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryReservationQuery;
import com.ailearn.platform.core.inventory.application.InventoryTransactionQuery;
import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryReservationPage;
import com.ailearn.platform.core.inventory.application.InventoryTransactionPage;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 库存持久化边界。
 * <p>
 * 该接口只由 inventory 应用服务使用；采购、销售、制造等领域只能调用
 * {@code InventoryCommandService}/{@code InventoryQueryService}，不得绕过应用端口。
 * 实现必须在数据库侧以 tenant_id 过滤，并对余额行使用 {@code FOR UPDATE}。
 * </p>
 */
public interface InventoryRepository {

    /**
     * 按完整库存维度加行锁；不存在时原子创建零余额行后重新加锁。
     *
     * @param tenantId 可信租户
     * @param dimension 库存维度
     * @param operatorId 操作用户
     * @return 已锁定的余额
     */
    InventoryBalance lockOrCreateBalance(UUID tenantId, InventoryDimension dimension, UUID operatorId);

    /**
     * 按稳定维度键顺序锁定多个余额，避免双边移动死锁。
     *
     * @param tenantId 可信租户
     * @param dimensions 待锁定维度
     * @param operatorId 操作用户
     * @return 与排序后维度一致的已锁定余额
     */
    List<InventoryBalance> lockBalancesInStableOrder(UUID tenantId,
                                                     Collection<InventoryDimension> dimensions,
                                                     UUID operatorId);

    /**
     * 以版本条件更新一条余额，保证 onHand/reserved 同行写入。
     *
     * @param balance 已锁定余额
     * @param onHandQty 新实物数量
     * @param reservedQty 新有效预留数量
     * @param businessTime 最近事实时间
     * @param operatorId 操作用户
     * @return 更新后的余额
     */
    InventoryBalance updateBalance(InventoryBalance balance,
                                   BigDecimal onHandQty,
                                   BigDecimal reservedQty,
                                   OffsetDateTime businessTime,
                                   UUID operatorId);

    /**
     * 锁定租户内的预留事实。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @return 已锁定预留，不存在时返回 null
     */
    InventoryReservation lockReservation(UUID tenantId, UUID reservationId);

    /**
     * 创建一笔新的库存预留。
     *
     * @param reservation 预留事实
     * @return 已写入的预留
     */
    InventoryReservation insertReservation(InventoryReservation reservation);

    /**
     * 以明确操作人写入预留；保留单参数方法兼容已有适配器。
     *
     * @param reservation 预留事实
     * @param operatorId 可信操作人
     * @return 已写入的预留
     */
    default InventoryReservation insertReservation(InventoryReservation reservation, UUID operatorId) {
        return insertReservation(reservation);
    }

    /**
     * 创建预留在指定维度的分配。
     *
     * @param allocation 分配事实
     * @return 已写入的分配
     */
    InventoryReservationAllocation insertAllocation(InventoryReservationAllocation allocation);

    /**
     * 以明确操作人写入分配；保留单参数方法兼容已有适配器。
     *
     * @param allocation 分配事实
     * @param operatorId 可信操作人
     * @return 已写入的分配
     */
    default InventoryReservationAllocation insertAllocation(InventoryReservationAllocation allocation,
                                                            UUID operatorId) {
        return insertAllocation(allocation);
    }

    /**
     * 锁定指定预留下的有效分配，结果按分配 ID 稳定排序。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @param allocationId 可选分配 ID
     * @param dimension 必须匹配的分配维度
     * @return 已锁定的有效分配
     */
    List<InventoryReservationAllocation> lockActiveAllocations(UUID tenantId,
                                                                UUID reservationId,
                                                                UUID allocationId,
                                                                InventoryDimension dimension);

    /**
     * 释放一条分配的有效数量并递增版本。
     *
     * @param allocation 已锁定分配
     * @param quantity 释放数量
     * @param operatorId 操作用户
     * @return 更新后的分配
     */
    InventoryReservationAllocation releaseAllocation(InventoryReservationAllocation allocation,
                                                      BigDecimal quantity,
                                                      UUID operatorId);

    /**
     * 迁移分配位置；部分迁移时拆分为源历史分配和目标新分配。
     *
     * @param allocation 已锁定源分配
     * @param targetDimension 目标维度
     * @param quantity 迁移数量
     * @param operatorId 操作用户
     * @return 迁移后受影响的分配集合
     */
    List<InventoryReservationAllocation> moveAllocation(InventoryReservationAllocation allocation,
                                                         InventoryDimension targetDimension,
                                                         BigDecimal quantity,
                                                         UUID operatorId);

    /**
     * 递增预留释放数量并更新状态。
     *
     * @param reservation 已锁定预留
     * @param quantity 释放数量
     * @param operatorId 操作用户
     * @return 更新后的预留
     */
    InventoryReservation releaseReservation(InventoryReservation reservation,
                                             BigDecimal quantity,
                                             UUID operatorId);

    /**
     * 追加库存流水，禁止更新既有流水。
     *
     * @param transaction 待追加库存事实
     * @return 已写入流水
     */
    InventoryTransaction appendTransaction(InventoryTransaction transaction);

    /**
     * 查询余额分页。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 租户隔离分页结果
     */
    InventoryBalancePage queryBalances(UUID tenantId, InventoryBalanceQuery query);

    /**
     * 查询预留及分配分页。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 租户隔离分页结果
     */
    InventoryReservationPage queryReservations(UUID tenantId, InventoryReservationQuery query);

    /**
     * 查询追加流水分页。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 租户隔离分页结果
     */
    InventoryTransactionPage queryTransactions(UUID tenantId, InventoryTransactionQuery query);

    /**
     * 查询指定库位的库存使用量，供主数据停用前置检查使用。
     *
     * @param tenantId 可信租户
     * @param locationId 库位 ID
     * @return 实物与有效预留聚合快照
     */
    LocationUsageSnapshot queryLocationUsage(UUID tenantId, UUID locationId);
}
