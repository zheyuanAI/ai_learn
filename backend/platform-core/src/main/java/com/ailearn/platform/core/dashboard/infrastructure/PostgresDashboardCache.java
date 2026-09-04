package com.ailearn.platform.core.dashboard.infrastructure;

import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import com.ailearn.platform.core.dashboard.ports.DashboardCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 看板投影 PostgreSQL 缓存。
 * <p>
 * 该实现只保存可丢弃的成功投影，缓存故障会退化为空缓存，不会把数据库异常或零值冒充为业务 Facts。
 * cache key 由看板应用服务按租户、权限指纹、摘要类型、时间范围和筛选项生成，解析后仍以 SQL 参数绑定。
 * </p>
 */
public class PostgresDashboardCache implements DashboardCache {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresDashboardCache(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<DashboardSummaryProjection> find(String key) {
        try {
            CacheKey cacheKey = CacheKey.parse(key);
            return jdbcTemplate.query("""
                    SELECT payload_json::text
                      FROM gis_dashboard_cache
                     WHERE tenant_id = ? AND permission_fingerprint = ?
                       AND summary_type = ? AND time_range_key = ?
                       AND filter_json = ?::jsonb AND isdel = 0
                    """, (resultSet, rowNum) -> read(resultSet), cacheKey.tenantId(),
                    cacheKey.permissionFingerprint(), cacheKey.summaryType().key(),
                    cacheKey.timeRangeKey(), cacheKey.filterJson()).stream().findFirst();
        } catch (IllegalArgumentException | DataAccessException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void save(String key, DashboardSummaryProjection projection, Instant savedAt) {
        try {
            CacheKey cacheKey = CacheKey.parse(key);
            String payload = objectMapper.writeValueAsString(projection);
            jdbcTemplate.update("""
                    INSERT INTO gis_dashboard_cache
                        (id, tenant_id, permission_fingerprint, summary_type, time_range_key,
                         filter_json, source_summary, payload_json, generated_at, stale_until,
                         created_at, updated_at, isdel)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, 0)
                    ON CONFLICT (tenant_id, permission_fingerprint, summary_type, time_range_key, filter_json)
                    DO UPDATE SET source_summary = EXCLUDED.source_summary,
                                  payload_json = EXCLUDED.payload_json,
                                  generated_at = EXCLUDED.generated_at,
                                  stale_until = EXCLUDED.stale_until,
                                  updated_at = EXCLUDED.updated_at,
                                  isdel = 0
                    """, UUID.randomUUID(), cacheKey.tenantId(), cacheKey.permissionFingerprint(),
                    cacheKey.summaryType().key(), cacheKey.timeRangeKey(), cacheKey.filterJson(),
                    projection.sourceSummary(), payload, projection.generatedAt(),
                    projection.generatedAt().plusSeconds(600), savedAt, savedAt);
        } catch (IllegalArgumentException | DataAccessException | JsonProcessingException ignored) {
            // 缓存仅是可丢弃加速层；失败时保留本次 Facts 查询结果，不改变业务语义。
        }
    }

    private DashboardSummaryProjection read(ResultSet resultSet) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString(1), DashboardSummaryProjection.class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("看板缓存投影无法解析", exception);
        }
    }

    private record CacheKey(UUID tenantId, String permissionFingerprint,
                            DashboardSummaryType summaryType, String timeRangeKey,
                            String filterJson) {
        private static CacheKey parse(String value) throws JsonProcessingException {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("看板缓存键不能为空");
            }
            String[] parts = value.split("\\|", 5);
            if (parts.length != 5) {
                throw new IllegalArgumentException("看板缓存键格式不合法");
            }
            Map<String, String> filters = new LinkedHashMap<>();
            if (!parts[4].isBlank()) {
                for (String item : parts[4].split("&")) {
                    String[] pair = item.split("=", 2);
                    if (pair.length != 2 || pair[0].isBlank()) {
                        throw new IllegalArgumentException("看板缓存筛选键格式不合法");
                    }
                    filters.put(pair[0], pair[1]);
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            return new CacheKey(UUID.fromString(parts[0]), parts[1],
                    DashboardSummaryType.parse(parts[2]), parts[3], mapper.writeValueAsString(filters));
        }
    }
}
