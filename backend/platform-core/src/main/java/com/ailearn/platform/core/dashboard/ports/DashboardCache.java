package com.ailearn.platform.core.dashboard.ports;

import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import java.time.Instant;
import java.util.Optional;

/** 看板成功投影缓存端口；缓存不是业务事实来源。 */
public interface DashboardCache {
    Optional<DashboardSummaryProjection> find(String key);
    void save(String key, DashboardSummaryProjection projection, Instant savedAt);
}
