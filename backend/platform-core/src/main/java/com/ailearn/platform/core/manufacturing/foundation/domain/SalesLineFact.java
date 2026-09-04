package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** 销售订单行只读事实；制造只引用它，不写入销售领域。 */
public record SalesLineFact(UUID id, UUID tenantId, UUID productId, BigDecimal orderedQty,
                            boolean active) {

    public SalesLineFact {
        Objects.requireNonNull(id, "salesOrderLineId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(productId, "productId 不能为空");
        if (orderedQty == null || orderedQty.signum() <= 0) {
            throw new IllegalArgumentException("销售明细数量必须大于 0");
        }
    }
}
