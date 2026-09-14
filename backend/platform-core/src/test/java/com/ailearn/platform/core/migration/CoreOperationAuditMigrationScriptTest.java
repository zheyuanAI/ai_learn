package com.ailearn.platform.core.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Core AI-01B 业务操作审计迁移的静态契约测试。 */
class CoreOperationAuditMigrationScriptTest {

    /** 校验服务本地审计表、租户索引、结果约束和幂等键脱敏字段。 */
    @Test
    void shouldContainTenantScopedStructuredOperationAudit() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/core/V10__core_operation_audit.sql")) {
            assertTrue(input != null, "Core V10 操作审计迁移必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("create table if not exists core_operation_audit_log"));
        assertTrue(sql.contains("tenant_id uuid not null"));
        assertTrue(sql.contains("actor_type in ('user', 'system', 'device')"));
        assertTrue(sql.contains("result in ('success', 'failed', 'denied')"));
        assertTrue(sql.contains("idempotency_key_hash varchar(64)"));
        assertFalse(sql.contains("idempotency_key varchar"), "不得保存原始幂等键");
        assertTrue(sql.contains("tenant_id, entity_type, entity_id, occurred_at desc"));
        assertTrue(sql.contains("where isdel = 0"));
    }
}
