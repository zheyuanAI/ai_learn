package com.ailearn.platform.core.manufacturing.execution.infrastructure;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycleRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/**
 * focused tests 使用的内存生命周期适配器。
 * <p>生产运行时由 PostgreSQL 生命周期适配器承载快照；状态规则仍保留在应用层和生命周期聚合。</p>
 */
public class InMemoryWorkOrderLifecycleRepository implements WorkOrderLifecycleRepository {

    private final ConcurrentMap<ScopedWorkOrderId, WorkOrderLifecycle> store = new ConcurrentHashMap<>();

    /** 按租户读取工单，跨租户工单表现为不存在。 */
    @Override
    public Optional<WorkOrderLifecycle> find(UUID tenantId, UUID workOrderId) {
        return Optional.ofNullable(store.get(new ScopedWorkOrderId(tenantId, workOrderId)));
    }

    /** 保存首次生命周期登记，避免 foundation 幂等重放时重复创建状态。 */
    @Override
    public WorkOrderLifecycle saveIfAbsent(WorkOrderLifecycle lifecycle) {
        ScopedWorkOrderId key = new ScopedWorkOrderId(lifecycle.workOrder().tenantId(), lifecycle.workOrder().id());
        return store.putIfAbsent(key, lifecycle) == null ? lifecycle : store.get(key);
    }

    /** 在单个租户工单键上串行执行状态更新，保证单进程单工单状态不会丢失。 */
    @Override
    public WorkOrderLifecycle update(UUID tenantId, UUID workOrderId,
                                     UnaryOperator<WorkOrderLifecycle> updater) {
        ScopedWorkOrderId key = new ScopedWorkOrderId(tenantId, workOrderId);
        return store.compute(key, (ignored, current) -> {
            if (current == null) {
                return null;
            }
            return updater.apply(current);
        });
    }

    /** 租户范围内的工单键，避免同一 UUID 在不同租户间互相覆盖。 */
    private record ScopedWorkOrderId(UUID tenantId, UUID workOrderId) {
    }
}
