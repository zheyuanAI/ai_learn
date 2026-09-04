package com.ailearn.platform.core.manufacturing.dispatch.domain;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 派工聚合持久化端口；内存实现仅用于当前阶段可验证层。 */
public interface DispatchRepository {
    Optional<DispatchOrder> find(UUID tenantId, UUID id);
    DispatchOrder saveIfAbsent(DispatchOrder order);
    DispatchOrder update(UUID tenantId, UUID id, UnaryOperator<DispatchOrder> updater);
}
