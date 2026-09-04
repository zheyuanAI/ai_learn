package com.ailearn.platform.core.transfer.domain;

import java.time.OffsetDateTime;
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
