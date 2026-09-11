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

    /**
     * 采购来源允许引用工单产出品或该工单锁定 BOM 中的组件物料。
     * 该关联只表达人工供需与追溯关系，不触发 MRP，也不修改制造事实。
     */
    @Override
    default Optional<WorkOrderSourceFact> findActiveForProcurement(UUID tenantId, UUID workOrderId,
                                                                   UUID productId) {
        Optional<WorkOrderSourceFact> source = findActiveWorkOrder(tenantId, workOrderId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        if (source.get().matches(tenantId, productId)) {
            return source;
        }
        return findWorkOrder(tenantId, workOrderId)
                .filter(workOrder -> !workOrder.deleted() && workOrder.bomId() != null)
                .flatMap(workOrder -> findActiveBom(tenantId, workOrder.bomId()))
                .filter(bom -> bom.components().stream()
                        .anyMatch(component -> component.componentProductId().equals(productId)))
                .map(ignored -> source.get());
    }

    /** 保存 BOM 事实。 */
    BomFact saveBom(BomFact bom);

    /** 保存 Routing 事实。 */
    RoutingFact saveRouting(RoutingFact routing);

    /** 保存工单生产意图。 */
    WorkOrderFact saveWorkOrder(WorkOrderFact workOrder);

    /** 按租户更新 Draft/Rejected 工单生产意图，并使用基础事实版本做并发保护。 */
    WorkOrderFact updateWorkOrder(WorkOrderFact workOrder, long expectedVersion);

    /** 查询当前租户未删除 BOM 列表。 */
    default List<BomFact> findBoms(UUID tenantId) { return List.of(); }

    /** 查询当前租户未删除 Routing 列表。 */
    default List<RoutingFact> findRoutings(UUID tenantId) { return List.of(); }

    /** 查询当前租户单个未删除 BOM。 */
    default Optional<BomFact> findBom(UUID tenantId, UUID bomId) {
        return findBoms(tenantId).stream().filter(item -> item.id().equals(bomId)).findFirst();
    }

    /** 查询当前租户单个未删除 Routing。 */
    default Optional<RoutingFact> findRouting(UUID tenantId, UUID routingId) {
        return findRoutings(tenantId).stream().filter(item -> item.id().equals(routingId)).findFirst();
    }

    /** 按租户读取完整工单生产意图，供执行生命周期重启恢复。 */
    Optional<WorkOrderFact> findWorkOrder(UUID tenantId, UUID workOrderId);

    /** 按租户读取未删除工单，供制造看板汇总复用基础事实。 */
    List<WorkOrderFact> findWorkOrders(UUID tenantId);

    /** 按租户统计工单数量，供幂等测试和监控使用。 */
    long countWorkOrders(UUID tenantId);

    /**
     * 在当前租户范围锁定工单主事实，供派工累计数量检查串行化；没有匹配工单时由适配器抛出受控异常。
     *
     * @param tenantId 可信租户
     * @param workOrderId 工单标识
     */
    default void lockWorkOrder(UUID tenantId, UUID workOrderId) {
        // focused 内存适配器不需要数据库行锁。
    }
}
