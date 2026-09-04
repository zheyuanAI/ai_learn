package com.ailearn.platform.core.dashboard.ports;

import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 可测试的内存缓存实现，实际缓存失效由看板应用服务按时间语义控制。 */
public class InMemoryDashboardCache implements DashboardCache {
    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<DashboardSummaryProjection> find(String key) {
        CacheEntry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.projection());
    }

    @Override
    public void save(String key, DashboardSummaryProjection projection, Instant savedAt) {
        entries.put(key, new CacheEntry(projection, savedAt));
    }

    private record CacheEntry(DashboardSummaryProjection projection, Instant savedAt) {
    }
}
