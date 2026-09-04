package com.ailearn.platform.core.manufacturing.operation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 工序执行持久化端口；生产实现可替换为 PostgreSQL。 */
public interface OperationExecutionRepository {
    Optional<OperationExecution> find(UUID tenantId, UUID id);
    OperationExecution saveIfAbsent(OperationExecution execution);
    OperationExecution update(UUID tenantId, UUID id, UnaryOperator<OperationExecution> updater);
    List<OperationExecution> findByDevice(UUID tenantId, UUID deviceId);
}
