package com.ailearn.platform.core.traceability.ports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 领域应用服务提供的最小事实摘要，不在 Core S7 复制源事实。 */
public record FactsSummary(Map<String, BigDecimal> metrics, String sourceSummary,
                           Instant sourceUpdatedAt) {

    public FactsSummary {
        Map<String, BigDecimal> safeMetrics = new LinkedHashMap<>();
        if (metrics != null) {
            metrics.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    safeMetrics.put(key, value);
                }
            });
        }
        metrics = Collections.unmodifiableMap(safeMetrics);
        sourceSummary = sourceSummary == null ? "" : sourceSummary;
    }
}
