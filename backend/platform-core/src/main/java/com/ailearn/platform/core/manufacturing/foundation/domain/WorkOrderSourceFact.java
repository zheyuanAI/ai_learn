package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** 下游采购等领域使用的最小工单来源事实，避免依赖制造内部执行模型。 */
public record WorkOrderSourceFact(UUID id, UUID tenantId, UUID productId, String workOrderNo,
                                  BigDecimal plannedQty, WorkOrderStatus status, boolean deleted) {

    public WorkOrderSourceFact {
        Objects.requireNonNull(id, "workOrderId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(productId, "productId 不能为空");
        if (workOrderNo == null || workOrderNo.isBlank()) {
            throw new IllegalArgumentException("workOrderNo 不能为空");
        }
        Objects.requireNonNull(plannedQty, "plannedQty 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
    }

    /** 判断来源是否属于租户且产品一致。 */
    public boolean matches(UUID requestedTenantId, UUID requestedProductId) {
        return !deleted && tenantId.equals(requestedTenantId) && productId.equals(requestedProductId);
    }
}
