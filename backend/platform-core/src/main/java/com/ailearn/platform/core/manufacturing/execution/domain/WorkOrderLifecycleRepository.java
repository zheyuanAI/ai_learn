package com.ailearn.platform.core.manufacturing.execution.domain;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 工单生命周期持久化端口；实现可以替换为 PostgreSQL，不改变应用层状态规则。 */
public interface WorkOrderLifecycleRepository {

    /** 按可信租户查询工单生命周期。 */
    Optional<WorkOrderLifecycle> find(UUID tenantId, UUID workOrderId);

    /**
     * 按租户批量查询工单生命周期，供列表读模型一次性组装执行进度和动作能力。
     * 入参：可信租户与当前分页工单 ID 集合；出参：同租户且存在生命周期快照的聚合列表；流程：默认实现复用单项查询，
     * PostgreSQL 适配器可覆盖为单次查询以避免列表页产生 N+1 查询。
     */
    default List<WorkOrderLifecycle> findAll(UUID tenantId, Set<UUID> workOrderIds) {
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            return List.of();
        }
        return workOrderIds.stream()
                .map(id -> find(tenantId, id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 保存首次登记的生命周期；重复登记返回已存在的同一聚合。 */
    WorkOrderLifecycle saveIfAbsent(WorkOrderLifecycle lifecycle);

    /** 在租户和工单键范围内原子替换生命周期。 */
    WorkOrderLifecycle update(UUID tenantId, UUID workOrderId,
                              UnaryOperator<WorkOrderLifecycle> updater);
}
