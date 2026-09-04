package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.gis.domain.MapAssetMetadata;
import com.ailearn.platform.core.gis.domain.MapEntityType;
import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import com.ailearn.platform.core.gis.infrastructure.PostgresGisConfigurationStore;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GIS PostgreSQL 适配器的租户 SQL 和幂等写入 focused 测试，不连接任何数据库。 */
class PostgresGisConfigurationStoreTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void shouldWriteMapAndAssetWithExplicitTenantColumns() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PostgresGisConfigurationStore store = new PostgresGisConfigurationStore(jdbcTemplate);
        Instant now = Instant.parse("2026-09-04T00:00:00Z");
        SiteMapConfiguration map = new SiteMapConfiguration(UUID.randomUUID(), TENANT, "factory", "厂区",
                new MapAssetMetadata("factory.png", "image/png", 1024, HASH), now, now);

        store.saveMap(map);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getAllValues().stream().allMatch(value -> value.toLowerCase().contains("tenant_id")));
        assertTrue(sql.getAllValues().stream().anyMatch(value -> value.contains("gis_site_map_asset")));
    }

    @Test
    void shouldPersistPointIdempotencyKeyAndDigestWithoutSourceTableAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PostgresGisConfigurationStore store = new PostgresGisConfigurationStore(jdbcTemplate);
        Instant now = Instant.parse("2026-09-04T00:00:00Z");
        MapPointConfiguration point = new MapPointConfiguration(UUID.randomUUID(), TENANT,
                UUID.randomUUID(), MapEntityType.DEVICE, UUID.randomUUID(), 10, 20, 0, "", now, now);

        store.savePoint(point, "point-key", HASH);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        String insert = sql.getValue().toLowerCase();
        assertTrue(insert.contains("gis_map_point"));
        assertTrue(insert.contains("tenant_id"));
        assertTrue(insert.contains("idempotency_key"));
        assertTrue(insert.contains("payload_digest"));
        assertTrue(insert.contains("on conflict"));
        assertTrue(!insert.contains("gis_site_map") || insert.contains("site_map_id"));
    }
}
