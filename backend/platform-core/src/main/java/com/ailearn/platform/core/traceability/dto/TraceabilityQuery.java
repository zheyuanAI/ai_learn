package com.ailearn.platform.core.traceability.dto;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.util.Objects;
import java.util.UUID;

/** 追溯链查询入口；租户只从可信上下文取得。 */
public record TraceabilityQuery(FactsQueryContext context, String entityType, UUID entityId) {
    public TraceabilityQuery {
        Objects.requireNonNull(context, "查询上下文不能为空");
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType 不能为空");
        }
        Objects.requireNonNull(entityId, "entityId 不能为空");
    }

    /** 转换为跨域 Facts 端口使用的最小查询对象。 */
    public TraceQuery toPortQuery() {
        return new TraceQuery(context, entityType, entityId);
    }
}
