package com.ailearn.platform.core.transfer.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 调拨聚合持久化端口；库存变化不在此端口直接写入。
 */
public interface TransferRepository {

    /**
     * 创建草稿调拨单及其明细。
     *
     * @param order 调拨聚合
     * @return 已写入聚合
     */
    TransferOrder insert(TransferOrder order);

    /**
     * 查询当前租户内未删除调拨单。
     *
     * @param tenantId 可信租户
     * @param id 调拨单 ID
     * @return 调拨聚合或空
     */
    Optional<TransferOrder> findById(UUID tenantId, UUID id);

    /**
     * 查询当前租户的调拨分页；实现必须在表头和统计 SQL 中同时带租户与逻辑删除条件。
     *
     * @param tenantId 可信租户
     * @param offset 零基偏移量
     * @param limit 当前页大小
     * @param status 可选状态
     * @param keyword 可选调拨单号关键词
     * @return 当前页聚合和总数
     */
    TransferPage findPage(UUID tenantId, int offset, int limit, String status, String keyword);

    /**
     * 用版本条件将草稿推进为已确认。
     *
     * @param tenantId 可信租户
     * @param id 调拨单 ID
     * @param expectedVersion 预期版本
     * @param operatorId 可信确认人
     * @param confirmedAt 确认时间
     * @return 更新成功返回 true
     */
    boolean confirm(UUID tenantId, UUID id, long expectedVersion, UUID operatorId, OffsetDateTime confirmedAt);
}
