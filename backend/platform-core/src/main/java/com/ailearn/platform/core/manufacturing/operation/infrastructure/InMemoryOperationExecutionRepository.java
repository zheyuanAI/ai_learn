package com.ailearn.platform.core.manufacturing.operation.infrastructure;

import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/**
 * 工序执行 focused 测试和当前无表环境的内存适配器。
 * <p>生产风险：未接 PostgreSQL，进程重启后工序时间线和设备关联会丢失。</p>
 */
public class InMemoryOperationExecutionRepository implements OperationExecutionRepository {
    private final ConcurrentMap<Key, OperationExecution> store = new ConcurrentHashMap<>();

    @Override
    public Optional<OperationExecution> find(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(new Key(tenantId, id)));
    }

    @Override
    public OperationExecution saveIfAbsent(OperationExecution execution) {
        Key key = new Key(execution.tenantId(), execution.id());
        OperationExecution current = store.putIfAbsent(key, execution);
        return current == null ? execution : current;
    }

    @Override
    public OperationExecution update(UUID tenantId, UUID id,
                                     UnaryOperator<OperationExecution> updater) {
        return store.compute(new Key(tenantId, id),
                (ignored, current) -> current == null ? null : updater.apply(current));
    }

    @Override
    public List<OperationExecution> findByDevice(UUID tenantId, UUID deviceId) {
        return store.entrySet().stream()
                .filter(entry -> entry.getKey().tenantId().equals(tenantId))
                .map(java.util.Map.Entry::getValue)
                .filter(value -> deviceId.equals(value.deviceId()))
                .toList();
    }

    private record Key(UUID tenantId, UUID id) { }
}
