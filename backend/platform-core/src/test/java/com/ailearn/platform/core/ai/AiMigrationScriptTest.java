package com.ailearn.platform.core.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Core V9 AI 会话与工具审计迁移的静态契约检查。 */
class AiMigrationScriptTest {

    /** 校验 AI 表、租户字段、受控状态和逻辑删除索引。 */
    @Test
    void shouldContainTenantScopedChatAndToolAuditTables() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/core/V9__ai_chat_and_tool_audit.sql")) {
            assertNotNull(input, "Core V9 AI 迁移脚本必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        for (String table : List.of("ai_chat_session", "ai_chat_message", "ai_tool_audit_log")) {
            assertTrue(sql.contains("create table if not exists " + table), "V9 缺少表: " + table);
        }
        assertTrue(sql.split("tenant_id uuid not null", -1).length - 1 >= 3,
                "AI 会话、消息和审计表必须包含非空 tenant_id");
        assertTrue(sql.contains("status in ('success', 'failed', 'timeout', 'denied')"),
                "工具审计必须约束成功、失败、超时和拒绝状态");
        assertTrue(sql.contains("navigation_action_summary"),
                "助手消息必须保存建议导航动作摘要");
        assertTrue(sql.contains("where isdel = 0"),
                "AI 查询索引必须排除逻辑删除数据");
    }
}
