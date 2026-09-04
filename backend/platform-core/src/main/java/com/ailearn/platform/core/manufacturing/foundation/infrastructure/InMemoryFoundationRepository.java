package com.ailearn.platform.core.manufacturing.foundation.infrastructure;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.SalesLineFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderSourceFact;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * foundation focused tests 使用的可替换内存适配器。
 * <p>
 * 生产运行时由 PostgreSQL 适配器承载事实；本实现不注册为 Spring Bean，避免测试适配器与生产适配器
 * 竞争同一组 foundation 端口。
 * </p>
 */
public class InMemoryFoundationRepository implements FoundationRepository {

    private final ConcurrentMap<UUID, BomFact> boms = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RoutingFact> routings = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SalesLineFact> salesLines = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, WorkOrderFact> workOrders = new ConcurrentHashMap<>();

    @Override
    public Optional<BomFact> findActiveBom(UUID tenantId, UUID bomId) {
        if (tenantId == null || bomId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(boms.get(bomId))
                .filter(bom -> bom.isActiveFor(tenantId, bom.productId()));
    }

    @Override
    public Optional<RoutingFact> findActiveRouting(UUID tenantId, UUID routingId) {
        if (tenantId == null || routingId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(routings.get(routingId))
                .filter(routing -> routing.isActiveFor(tenantId, routing.productId()));
    }

    @Override
    public Optional<SalesLineFact> findActiveLine(UUID tenantId, UUID salesOrderLineId) {
        if (tenantId == null || salesOrderLineId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(salesLines.get(salesOrderLineId))
                .filter(line -> line.active() && line.tenantId().equals(tenantId));
    }

    @Override
    public Optional<WorkOrderSourceFact> findActiveWorkOrder(UUID tenantId, UUID workOrderId) {
        if (tenantId == null || workOrderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(workOrders.get(workOrderId))
                .filter(workOrder -> !workOrder.deleted() && workOrder.tenantId().equals(tenantId))
                .map(WorkOrderFact::sourceFact);
    }

    @Override
    public BomFact saveBom(BomFact bom) {
        return putOnce(boms, bom.id(), bom);
    }

    @Override
    public RoutingFact saveRouting(RoutingFact routing) {
        return putOnce(routings, routing.id(), routing);
    }

    @Override
    public WorkOrderFact saveWorkOrder(WorkOrderFact workOrder) {
        return putOnce(workOrders, workOrder.id(), workOrder);
    }

    /** 按租户读取完整工单生产意图，避免测试适配器绕过租户边界。 */
    @Override
    public Optional<WorkOrderFact> findWorkOrder(UUID tenantId, UUID workOrderId) {
        if (tenantId == null || workOrderId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(workOrders.get(workOrderId))
                .filter(workOrder -> workOrder.tenantId().equals(tenantId) && !workOrder.deleted());
    }

    /** 按租户返回未删除工单，保持 focused 适配器与生产查询端口语义一致。 */
    @Override
    public List<WorkOrderFact> findWorkOrders(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return workOrders.values().stream()
                .filter(workOrder -> tenantId.equals(workOrder.tenantId()) && !workOrder.deleted())
                .sorted(java.util.Comparator.comparing(WorkOrderFact::createdAt)
                        .thenComparing(WorkOrderFact::id))
                .toList();
    }

    /**
     * 测试或未来销售适配器接入时写入销售事实；制造应用服务不会调用该方法。
     *
     * @param line 销售订单行事实
     * @return 已保存的销售行
     */
    public SalesLineFact saveSalesLine(SalesLineFact line) {
        return putOnce(salesLines, line.id(), line);
    }

    @Override
    public long countWorkOrders(UUID tenantId) {
        return workOrders.values().stream()
                .filter(workOrder -> workOrder.tenantId().equals(tenantId))
                .count();
    }

    private static <T> T putOnce(ConcurrentMap<UUID, T> target, UUID id, T value) {
        T previous = target.putIfAbsent(id, value);
        if (previous != null) {
            throw new IllegalStateException("foundation 事实 ID 已存在: " + id);
        }
        return value;
    }
}
