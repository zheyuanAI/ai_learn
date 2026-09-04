package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** BOM 版本事实；逻辑删除通过 deleted 标志表达，不物理覆盖历史版本。 */
public record BomFact(UUID id, UUID tenantId, UUID productId, String bomCode, String version,
                      BomStatus status, List<BomComponentFact> components, boolean deleted,
                      UUID createdBy, OffsetDateTime createdAt) {

    public BomFact {
        Objects.requireNonNull(id, "bomId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(productId, "productId 不能为空");
        requireText("bomCode", bomCode);
        requireText("version", version);
        Objects.requireNonNull(status, "status 不能为空");
        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("BOM 至少需要一条组件明细");
        }
        components = List.copyOf(components);
        Objects.requireNonNull(createdBy, "createdBy 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    /** 判断该版本能否作为指定产品的有效 BOM。 */
    public boolean isActiveFor(UUID requestedTenantId, UUID requestedProductId) {
        return !deleted && status == BomStatus.ACTIVE
                && tenantId.equals(requestedTenantId) && productId.equals(requestedProductId);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
