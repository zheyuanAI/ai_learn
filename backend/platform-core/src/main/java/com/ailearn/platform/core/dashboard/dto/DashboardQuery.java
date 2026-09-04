package com.ailearn.platform.core.dashboard.dto;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** 看板查询条件；tenantId 不在客户端参数中出现。 */
public record DashboardQuery(FactsQueryContext context, String timeRange, Map<String, String> filters) {
    public DashboardQuery {
        Objects.requireNonNull(context, "查询上下文不能为空");
        filters = filters == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(filters));
    }
}
