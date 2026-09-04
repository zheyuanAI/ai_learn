package com.ailearn.platform.core.stocktake.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 盘点单持久化端口；应用层不直接依赖 MyBatis Mapper。
 */
public interface StocktakeRepository {

    /**
     * 插入未盘点盘点单。
     *
     * @param order 盘点聚合
     * @return 已持久化聚合
     */
    StocktakeOrder insert(StocktakeOrder order);

    /**
     * 按可信租户读取盘点聚合。
     *
     * @param tenantId 可信租户
     * @param id 盘点单 ID
     * @return 盘点聚合，可为空
     */
    Optional<StocktakeOrder> findById(UUID tenantId, UUID id);

    /**
     * 以版本条件开始盘点。
     *
     * @param tenantId 可信租户
     * @param id 盘点单 ID
     * @param expectedVersion 期望版本
     * @param operatorId 操作用户
     * @param startedAt 开始时间
     * @return 是否更新成功
     */
    boolean start(UUID tenantId, UUID id, long expectedVersion,
                  UUID operatorId, OffsetDateTime startedAt);

    /**
     * 批量插入系统快照明细。
     *
     * @param lines 系统快照明细
     * @param operatorId 操作用户
     * @return 已插入行数
     */
    int insertLines(UUID orderId, List<StocktakeLine> lines, UUID operatorId);

    /**
     * 更新实盘/差异流水并以版本条件确认表头。
     *
     * @param tenantId 可信租户
     * @param id 盘点单 ID
     * @param expectedVersion 期望版本
     * @param lines 已确认明细
     * @param operatorId 操作用户
     * @param confirmedAt 确认时间
     * @return 是否完整确认成功
     */
    boolean confirm(UUID tenantId, UUID id, long expectedVersion, List<StocktakeLine> lines,
                    UUID operatorId, OffsetDateTime confirmedAt);
}
