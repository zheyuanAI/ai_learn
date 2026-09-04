package com.ailearn.platform.core.traceability.ports;

import java.util.Objects;
import java.util.UUID;

/** 追溯入口查询条件；关系由各事实端口返回，不在 S7 建立万能关系表。 */
public record TraceQuery(FactsQueryContext context, String entityType, UUID entityId) {

    public TraceQuery {
        Objects.requireNonNull(context, "查询上下文不能为空");
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType 不能为空");
        }
        Objects.requireNonNull(entityId, "entityId 不能为空");
    }
}
