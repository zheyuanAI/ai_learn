package com.ailearn.platform.core.manufacturing.execution.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 工单生命周期持久化端口；实现可以替换为 PostgreSQL，不改变应用层状态规则。 */
public interface WorkOrderLifecycleRepository {

    /** 按可信租户查询工单生命周期。 */
    Optional<WorkOrderLifecycle> find(UUID tenantId, UUID workOrderId);

    /** 保存首次登记的生命周期；重复登记返回已存在的同一聚合。 */
    WorkOrderLifecycle saveIfAbsent(WorkOrderLifecycle lifecycle);

    /** 在租户和工单键范围内原子替换生命周期。 */
    WorkOrderLifecycle update(UUID tenantId, UUID workOrderId,
                              UnaryOperator<WorkOrderLifecycle> updater);
}
