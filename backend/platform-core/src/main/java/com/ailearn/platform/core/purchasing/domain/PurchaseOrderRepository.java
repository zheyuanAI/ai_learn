package com.ailearn.platform.core.purchasing.domain;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 采购订单和到货验收持久化边界；实现必须按 tenant_id 过滤并在业务写入前锁定订单。
 */
public interface PurchaseOrderRepository {

    /**
     * 创建 Draft 采购订单及明细。
     */
    PurchaseOrder insert(PurchaseOrder order);

    /**
     * 查询当前租户订单。
     */
    Optional<PurchaseOrder> findById(UUID tenantId, UUID id);

    /**
     * 在当前事务内以 FOR UPDATE 读取订单和明细。
     */
    Optional<PurchaseOrder> findByIdForUpdate(UUID tenantId, UUID id);

    /**
     * 按真实采购订单行 ID 读取已发生事实累计，不按产品或批次猜测归属。
     * 入参：可信租户与当前订单 ID；出参：有效订单行到五项累计的映射，零事实行允许缺席。
     */
    Map<UUID, PurchaseOrderLineCumulative> findLineCumulatives(UUID tenantId, UUID orderId);

    /**
     * 以版本条件更新 Draft 字段。
     */
    PurchaseOrder updateDraft(PurchaseOrder order, long expectedVersion);

    /**
     * 以版本条件推进生命周期或写入完成审计。
     */
    PurchaseOrder updateState(PurchaseOrder order, long expectedVersion);

    /**
     * 在当前事务内写入已确认到货验收及明细。
     */
    PurchaseReceipt insertReceipt(PurchaseReceipt receipt);

    /**
     * 查询当前租户采购订单分页。
     */
    PurchaseOrderPage findPage(UUID tenantId, PurchaseOrderPageQuery query);
}
