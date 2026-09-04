package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** BOM 组件只读事实；数量使用 BigDecimal 保持数据库 NUMERIC 精度。 */
public record BomComponentFact(UUID componentProductId, BigDecimal quantity, String uom,
                               BigDecimal scrapRate) {

    public BomComponentFact {
        Objects.requireNonNull(componentProductId, "componentProductId 不能为空");
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("componentQty 必须大于 0");
        }
        if (uom == null || uom.isBlank()) {
            throw new IllegalArgumentException("uom 不能为空");
        }
        if (scrapRate != null && scrapRate.signum() < 0) {
            throw new IllegalArgumentException("scrapRate 不能为负数");
        }
        uom = uom.trim();
    }
}
