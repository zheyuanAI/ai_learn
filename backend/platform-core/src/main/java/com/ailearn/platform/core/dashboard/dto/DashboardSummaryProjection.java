package com.ailearn.platform.core.dashboard.dto;

import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.domain.DashboardTimeRange;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 看板摘要投影，stale=true 时明确表示数据不是本次实时查询结果。 */
public record DashboardSummaryProjection(@JsonProperty("summary_type") DashboardSummaryType summaryType,
                                         Map<String, BigDecimal> metrics,
                                         @JsonProperty("time_range") DashboardTimeRange timeRange,
                                         @JsonProperty("source_summary") String sourceSummary,
                                         @JsonProperty("generated_at") Instant generatedAt,
                                         @JsonProperty("source_updated_at") Instant sourceUpdatedAt,
                                         boolean stale,
                                         @JsonProperty("stale_since") Instant staleSince,
                                         @JsonProperty("request_id") String requestId) {
    public DashboardSummaryProjection {
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
