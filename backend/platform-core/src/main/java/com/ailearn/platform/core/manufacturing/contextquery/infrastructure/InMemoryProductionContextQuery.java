package com.ailearn.platform.core.manufacturing.contextquery.infrastructure;

import com.ailearn.platform.core.manufacturing.contextquery.domain.ProductionContext;
import com.ailearn.platform.core.manufacturing.contextquery.exception.ProductionContextErrorCode;
import com.ailearn.platform.core.manufacturing.contextquery.exception.ProductionContextException;
import com.ailearn.platform.core.manufacturing.contextquery.port.ProductionContextQuery;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 从工序执行端口读取活动上下文的内存适配器。
 * <p>生产风险：该适配器依赖内存工序仓储；没有 PostgreSQL 查询快照与唯一约束，重启后不可恢复。</p>
 */
@Component
public class InMemoryProductionContextQuery implements ProductionContextQuery {
    private final OperationExecutionRepository repository;

    /** 创建生产上下文查询适配器。 */
    public InMemoryProductionContextQuery(OperationExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ProductionContext> findActive(UUID tenantId, UUID deviceId,
                                                   OffsetDateTime alarmTime) {
        if (tenantId == null || deviceId == null || alarmTime == null) {
            throw new ProductionContextException(ProductionContextErrorCode.MES_CONTEXT_001,
                    "tenantId、deviceId、alarmTime 均不能为空");
        }
        List<OperationExecution> active = repository.findByDevice(tenantId, deviceId).stream()
                .filter(value -> value.activeAt(alarmTime))
                .toList();
        if (active.size() > 1) {
            throw new ProductionContextException(ProductionContextErrorCode.MES_CONTEXT_002,
                    "同一设备告警时刻无法唯一定位工序");
        }
        return active.stream().map(value -> new ProductionContext(tenantId, deviceId,
                value.workOrderId(), value.id(), value.operationId(), value.startedAt(),
                value.eventAt(alarmTime))).findFirst();
    }
}
