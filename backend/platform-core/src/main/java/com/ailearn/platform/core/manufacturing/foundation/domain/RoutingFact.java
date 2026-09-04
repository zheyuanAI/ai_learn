package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Routing 版本事实；工单创建时复制版本号，后续审核下达时继续使用该版本。 */
public record RoutingFact(UUID id, UUID tenantId, UUID productId, String routingCode, String version,
                          RoutingStatus status, List<RoutingOperationFact> operations, boolean deleted,
                          UUID createdBy, OffsetDateTime createdAt) {

    public RoutingFact {
        Objects.requireNonNull(id, "routingId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(productId, "productId 不能为空");
        requireText("routingCode", routingCode);
        requireText("version", version);
        Objects.requireNonNull(status, "status 不能为空");
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("Routing 至少需要一道工序");
        }
        Set<Integer> operationNos = new HashSet<>();
        for (RoutingOperationFact operation : operations) {
            if (!operationNos.add(operation.operationNo())) {
                throw new IllegalArgumentException("Routing 的 operationNo 不能重复");
            }
        }
        operations = List.copyOf(operations);
        Objects.requireNonNull(createdBy, "createdBy 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }

    /** 判断该版本能否作为指定产品的有效 Routing。 */
    public boolean isActiveFor(UUID requestedTenantId, UUID requestedProductId) {
        return !deleted && status == RoutingStatus.ACTIVE
                && tenantId.equals(requestedTenantId) && productId.equals(requestedProductId);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
