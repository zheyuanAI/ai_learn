package com.ailearn.platform.core.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Core V6 GIS/看板迁移的静态结构契约，不连接开发数据库。 */
class CoreS7MigrationScriptTest {
    @Test
    void shouldContainTenantSafeMapPointAndDashboardCacheConstraints() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/core/V6__traceability_gis_dashboard.sql")) {
            assertTrue(input != null, "Core V6 迁移脚本必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        for (String table : List.of("gis_site_map", "gis_site_map_asset", "gis_map_point", "gis_dashboard_cache")) {
            assertTrue(sql.contains("create table if not exists " + table), "V6 缺少表: " + table);
        }
        assertTrue(sql.contains("background_type in ('png', 'jpeg', 'webp')"));
        assertTrue(sql.contains("mime_type in ('image/png', 'image/jpeg', 'image/webp')"));
        assertTrue(sql.contains("size_bytes <= 5242880"));
        assertTrue(sql.contains("x_percent >= 0 and x_percent <= 100"));
        assertTrue(sql.contains("y_percent >= 0 and y_percent <= 100"));
        assertTrue(sql.contains("where isdel = 0"));
        assertTrue(sql.contains("foreign key (tenant_id, site_map_id) references gis_site_map (tenant_id, id)"));
        assertTrue(sql.contains("permission_fingerprint"));
        assertTrue(sql.contains("payload_json jsonb"));
    }
}
