package com.ailearn.platform.core.manufacturing.foundation.domain;

import com.ailearn.platform.core.manufacturing.foundation.domain.port.BomFactsPort;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.RoutingFactsPort;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.SalesFactsPort;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.WorkOrderSourcePort;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** foundation 写入适配器内部接口；下游领域只能依赖四个只读端口。 */
public interface FoundationRepository extends BomFactsPort, RoutingFactsPort,
        SalesFactsPort, WorkOrderSourcePort {

    /** 保存 BOM 事实。 */
    BomFact saveBom(BomFact bom);

    /** 保存 Routing 事实。 */
    RoutingFact saveRouting(RoutingFact routing);

    /** 保存工单生产意图。 */
    WorkOrderFact saveWorkOrder(WorkOrderFact workOrder);

    /** 按租户读取完整工单生产意图，供执行生命周期重启恢复。 */
    Optional<WorkOrderFact> findWorkOrder(UUID tenantId, UUID workOrderId);

    /** 按租户读取未删除工单，供制造看板汇总复用基础事实。 */
    List<WorkOrderFact> findWorkOrders(UUID tenantId);

    /** 按租户统计工单数量，供幂等测试和监控使用。 */
    long countWorkOrders(UUID tenantId);
}
