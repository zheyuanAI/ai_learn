package com.ailearn.platform.core.manufacturing.dispatch.infrastructure;

import com.ailearn.platform.core.manufacturing.dispatch.port.WorkOrderReleasePort;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 将现有工单生命周期查询适配为派工/工序内部端口。
 * <p>只读取工单状态，不调用执行层写方法，避免派工安排伪造工单执行事实。</p>
 */
@Component
public class WorkOrderExecutionReleaseAdapter implements WorkOrderReleasePort {
    private final WorkOrderExecutionService workOrderExecutionService;

    /** 创建工单 Released 状态只读适配器。 */
    public WorkOrderExecutionReleaseAdapter(WorkOrderExecutionService workOrderExecutionService) {
        this.workOrderExecutionService = workOrderExecutionService;
    }

    @Override
    public boolean isReleased(UUID tenantId, UUID workOrderId) {
        return workOrderExecutionService.find(workOrderId)
                .filter(value -> tenantId.equals(value.workOrder().tenantId()))
                .map(value -> value.status() == WorkOrderStatus.Released)
                .orElse(false);
    }
}
