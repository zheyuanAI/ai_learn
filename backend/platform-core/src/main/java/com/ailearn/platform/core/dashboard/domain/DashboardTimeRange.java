package com.ailearn.platform.core.dashboard.domain;

import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 按租户时区计算的看板实际时间窗口。 */
public record DashboardTimeRange(String key, @JsonProperty("from") Instant from,
                                 @JsonProperty("to") Instant to) {

    public DashboardTimeRange {
        if (key == null || key.isBlank() || from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("看板时间范围不合法");
        }
    }

    /** 解析 today、7d、30d；未知值统一返回 GIS_QUERY_001。 */
    public static DashboardTimeRange parse(String requested, ZoneId zoneId, Instant now) {
        String key = requested == null || requested.isBlank() ? "today" : requested.trim().toLowerCase();
        int days = switch (key) {
            case "today" -> 1;
            case "7d" -> 7;
            case "30d" -> 30;
            default -> throw new GisException(GisErrorCode.GIS_QUERY_001, "不支持的 time_range: " + requested);
        };
        ZoneId safeZone = zoneId == null ? ZoneId.of("UTC") : zoneId;
        LocalDate endDate = now.atZone(safeZone).toLocalDate();
        Instant from = endDate.minusDays(days - 1L).atStartOfDay(safeZone).toInstant();
        return new DashboardTimeRange(key, from, now);
    }
}
