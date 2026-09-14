package com.ailearn.platform.core.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Core S8 AI 会话与工具审计迁移的静态契约测试。 */
class CoreAiMigrationScriptTest {

    /**
     * 用途：校验 AI 会话、消息和工具审计表及关键隔离约束存在。
     * 入参：无；出参：无；流程：读取 V9 SQL 并检查租户字段、状态白名单和复合外键。
     */
    @Test
    void shouldContainTenantScopedAiConversationAndAuditTables() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/core/V9__ai_chat_and_tool_audit.sql")) {
            assertTrue(input != null, "Core V9 AI 迁移脚本必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("create table if not exists ai_chat_session"));
        assertTrue(sql.contains("create table if not exists ai_chat_message"));
        assertTrue(sql.contains("create table if not exists ai_tool_audit_log"));
        assertTrue(sql.split("tenant_id uuid not null", -1).length - 1 >= 3,
                "三张 AI 表必须包含非空 tenant_id");
        assertTrue(sql.contains("foreign key (tenant_id, session_id) references ai_chat_session (tenant_id, id)"),
                "消息和审计必须通过租户复合外键绑定会话");
        assertTrue(sql.contains("status in ('success', 'failed', 'timeout', 'denied')"),
                "工具审计必须覆盖成功、失败、超时和拒绝状态");
        assertTrue(sql.contains("navigation_action_summary"),
                "助手消息必须保存已建议导航动作摘要");
        assertTrue(sql.contains("where isdel = 0"),
                "AI 查询索引必须排除逻辑删除数据");
    }
}
