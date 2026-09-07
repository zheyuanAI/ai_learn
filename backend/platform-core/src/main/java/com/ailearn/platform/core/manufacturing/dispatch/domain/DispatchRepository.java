package com.ailearn.platform.core.manufacturing.dispatch.domain;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 派工聚合持久化端口；内存实现仅用于当前阶段可验证层。 */
public interface DispatchRepository {
    Optional<DispatchOrder> find(UUID tenantId, UUID id);
    DispatchOrder saveIfAbsent(DispatchOrder order);
    DispatchOrder update(UUID tenantId, UUID id, UnaryOperator<DispatchOrder> updater);

    /**
     * 查询当前租户全部未删除派工事实；旧 focused 适配器可以使用空实现，正式 PostgreSQL 适配器覆盖该方法。
     *
     * @param tenantId 可信租户
     * @return 同租户派工事实
     */
    default List<DispatchOrder> findAll(UUID tenantId) {
        return List.of();
    }

    /**
     * 按工单读取派工事实，供累计派工数量校验；默认实现复用租户范围查询。
     *
     * @param tenantId 可信租户
     * @param workOrderId 工单标识
     * @return 同租户同工单派工事实
     */
    default List<DispatchOrder> findByWorkOrder(UUID tenantId, UUID workOrderId) {
        return findAll(tenantId).stream()
                .filter(value -> workOrderId.equals(value.workOrderId()))
                .toList();
    }
}
