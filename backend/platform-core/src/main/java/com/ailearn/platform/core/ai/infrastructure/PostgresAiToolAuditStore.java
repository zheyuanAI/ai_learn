package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.application.AiToolAuditStore;
import com.ailearn.platform.core.ai.domain.ToolAuditEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 使用参数绑定写入 PostgreSQL 的 AI 工具审计存储。 */
@Repository
public class PostgresAiToolAuditStore implements AiToolAuditStore {
    private final JdbcTemplate jdbcTemplate;

    /** 注入 Spring JDBC；只用于 AI 自有审计表，不访问其他领域表。 */
    public PostgresAiToolAuditStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ToolAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO ai_tool_audit_log
                    (id, tenant_id, user_id, session_id, request_id, tool_name,
                     input_summary, output_summary, source_summary, time_range_summary,
                     tool_call_summary, model_id, duration_ms, status, error_code,
                     error_reason, created_at, updated_at, isdel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, entry.id(), entry.tenantId(), entry.userId(), entry.sessionId(),
                entry.requestId(), entry.toolName(), entry.inputSummary(), entry.outputSummary(),
                entry.sourceSummary(), entry.timeRangeSummary(), entry.toolCallSummary(),
                entry.modelId(), entry.durationMs(), entry.status().name(), entry.errorCode(),
                entry.errorReason(), entry.createdAt(), entry.createdAt());
    }
}
