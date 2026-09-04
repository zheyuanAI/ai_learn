package com.ailearn.platform.core.manufacturing.dispatch.infrastructure;

import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchRepository;
import com.ailearn.platform.core.manufacturing.dispatch.port.DispatchReferencePort;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/**
 * 派工 focused 测试和本阶段无表环境使用的内存适配器。
 * <p>生产风险：未接入 PostgreSQL，进程重启后派工事实丢失，不能作为生产持久化实现。</p>
 */
public class InMemoryDispatchRepository implements DispatchRepository, DispatchReferencePort {
    private final ConcurrentMap<Key, DispatchOrder> store = new ConcurrentHashMap<>();

    @Override
    public Optional<DispatchOrder> find(UUID tenantId, UUID id) {
        return Optional.ofNullable(store.get(new Key(tenantId, id)));
    }

    @Override
    public DispatchOrder saveIfAbsent(DispatchOrder order) {
        Key key = new Key(order.tenantId(), order.id());
        DispatchOrder current = store.putIfAbsent(key, order);
        return current == null ? order : current;
    }

    @Override
    public DispatchOrder update(UUID tenantId, UUID id, UnaryOperator<DispatchOrder> updater) {
        Key key = new Key(tenantId, id);
        return store.compute(key, (ignored, current) -> current == null ? null : updater.apply(current));
    }

    private record Key(UUID tenantId, UUID id) { }
}
