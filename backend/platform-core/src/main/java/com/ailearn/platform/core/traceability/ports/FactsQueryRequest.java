package com.ailearn.platform.core.traceability.ports;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** 跨域事实摘要查询参数，所有筛选值只作为端口查询条件传递。 */
public record FactsQueryRequest(FactsQueryContext context, Instant from, Instant to,
                                Map<String, String> filters) {

    public FactsQueryRequest {
        Objects.requireNonNull(context, "查询上下文不能为空");
        Objects.requireNonNull(from, "查询起点不能为空");
        Objects.requireNonNull(to, "查询终点不能为空");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("查询终点必须晚于查询起点");
        }
        filters = filters == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(filters));
    }
}
