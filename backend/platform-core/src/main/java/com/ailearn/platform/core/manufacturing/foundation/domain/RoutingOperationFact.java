package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Routing 工序只读事实。 */
public record RoutingOperationFact(UUID id, int operationNo, String operationName,
                                   UUID workCenterId, BigDecimal standardTimeMinutes) {

    public RoutingOperationFact {
        Objects.requireNonNull(id, "operationId 不能为空");
        if (operationNo <= 0) {
            throw new IllegalArgumentException("operationNo 必须大于 0");
        }
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName 不能为空");
        }
        Objects.requireNonNull(workCenterId, "workCenterId 不能为空");
        if (standardTimeMinutes != null && standardTimeMinutes.signum() < 0) {
            throw new IllegalArgumentException("standardTimeMinutes 不能为负数");
        }
        operationName = operationName.trim();
    }
}
