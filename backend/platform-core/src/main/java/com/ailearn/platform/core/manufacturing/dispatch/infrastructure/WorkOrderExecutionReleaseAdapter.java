package com.ailearn.platform.core.manufacturing.dispatch.infrastructure;

import com.ailearn.platform.core.manufacturing.dispatch.port.WorkOrderReleasePort;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingOperationFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 将现有工单生命周期查询适配为派工/工序内部端口。
 * <p>只读取工单状态，不调用执行层写方法，避免派工安排伪造工单执行事实。</p>
 */
@Component
public class WorkOrderExecutionReleaseAdapter implements WorkOrderReleasePort {
    private final WorkOrderExecutionService workOrderExecutionService;
    private final FoundationRepository foundationRepository;

    /** 创建工单 Released 状态只读适配器。 */
    public WorkOrderExecutionReleaseAdapter(WorkOrderExecutionService workOrderExecutionService) {
        this(workOrderExecutionService, null);
    }

    /** 生产装配构造器；通过 foundation 事实校验 Routing、前置工序和工单行锁。 */
    @org.springframework.beans.factory.annotation.Autowired
    public WorkOrderExecutionReleaseAdapter(WorkOrderExecutionService workOrderExecutionService,
                                             FoundationRepository foundationRepository) {
        this.workOrderExecutionService = workOrderExecutionService;
        this.foundationRepository = foundationRepository;
    }

    @Override
    public boolean isReleased(UUID tenantId, UUID workOrderId) {
        return workOrderExecutionService.find(workOrderId)
                .filter(value -> tenantId.equals(value.workOrder().tenantId()))
                .map(value -> value.status() == WorkOrderStatus.Released)
                .orElse(false);
    }

    @Override
    public boolean isOperationInRouting(UUID tenantId, UUID workOrderId, UUID operationId) {
        if (foundationRepository == null) {
            return true;
        }
        return lifecycle(tenantId, workOrderId)
                .flatMap(value -> foundationRepository.findActiveRouting(tenantId,
                        value.workOrder().routingId()))
                .map(routing -> routing.operations().stream()
                        .anyMatch(operation -> operation.id().equals(operationId)))
                .orElse(false);
    }

    @Override
    public Optional<BigDecimal> plannedQty(UUID tenantId, UUID workOrderId) {
        return lifecycle(tenantId, workOrderId).map(value -> value.workOrder().plannedQty());
    }

    @Override
    public boolean arePredecessorsCompleted(UUID tenantId, UUID workOrderId, UUID operationId) {
        if (foundationRepository == null) {
            return true;
        }
        Optional<WorkOrderLifecycle> current = lifecycle(tenantId, workOrderId);
        if (current.isEmpty()) {
            return false;
        }
        Optional<RoutingFact> routing = foundationRepository.findActiveRouting(tenantId,
                current.get().workOrder().routingId());
        if (routing.isEmpty()) {
            return false;
        }
        Optional<RoutingOperationFact> target = routing.get().operations().stream()
                .filter(operation -> operation.id().equals(operationId)).findFirst();
        if (target.isEmpty()) {
            return false;
        }
        Set<UUID> completed = current.get().progress().completedOperationIds();
        return routing.get().operations().stream()
                .filter(operation -> operation.operationNo() < target.get().operationNo())
                .allMatch(operation -> completed.contains(operation.id()));
    }

    @Override
    public void lockWorkOrder(UUID tenantId, UUID workOrderId) {
        if (foundationRepository != null) {
            foundationRepository.lockWorkOrder(tenantId, workOrderId);
        }
    }

    private Optional<WorkOrderLifecycle> lifecycle(UUID tenantId, UUID workOrderId) {
        return workOrderExecutionService.find(workOrderId)
                .filter(value -> tenantId.equals(value.workOrder().tenantId()));
    }
}
