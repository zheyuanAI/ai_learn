package com.ailearn.platform.core.manufacturing.foundation.domain.port;

import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderSourceFact;
import java.util.Optional;
import java.util.UUID;

/**
 * 工单来源查询端口。
 * <p>
 * 采购等下游只通过此最小端口校验 sourceWorkOrderId 的同租户存在性和产品一致性，
 * 不建立跨阶段硬外键，也不获得制造执行写权限。
 * </p>
 */
public interface WorkOrderSourcePort {

    /**
     * 查询当前租户内未逻辑删除的工单来源事实。
     *
     * @param tenantId 可信租户
     * @param workOrderId 工单标识
     * @return 同租户来源事实，否则为空
     */
    Optional<WorkOrderSourceFact> findActiveWorkOrder(UUID tenantId, UUID workOrderId);

    /**
     * 校验下游引用的工单同时满足租户和产品一致性。
     *
     * @param tenantId 可信租户
     * @param workOrderId 工单标识
     * @param productId 下游单据产品
     * @return 产品一致的来源事实，否则为空
     */
    default Optional<WorkOrderSourceFact> findActiveForProduct(UUID tenantId, UUID workOrderId,
                                                                 UUID productId) {
        return findActiveWorkOrder(tenantId, workOrderId)
                .filter(source -> source.matches(tenantId, productId));
    }

    /**
     * 校验采购物料是否可追溯到指定工单。
     * 入参：可信租户、来源工单和采购物料；出参：可关联的工单来源事实。
     * 默认保持产出品一致校验，具备 BOM 事实的实现可同时接受该工单 BOM 组件物料。
     */
    default Optional<WorkOrderSourceFact> findActiveForProcurement(UUID tenantId, UUID workOrderId,
                                                                    UUID productId) {
        return findActiveForProduct(tenantId, workOrderId, productId);
    }
}
