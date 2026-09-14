package com.ailearn.platform.core.operationaudit.infrastructure;

import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.application.OperationAuditStore;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 使用参数绑定访问 Core 自有操作审计表，不读取其他服务数据库。 */
@Repository
public class PostgresOperationAuditStore implements OperationAuditStore {
    private final JdbcTemplate jdbcTemplate;

    /** 注入 Spring JDBC。 */
    public PostgresOperationAuditStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OperationAuditEntry entry) {
        jdbcTemplate.update("""
                INSERT INTO core_operation_audit_log
                    (id, tenant_id, actor_type, actor_id, actor_account, session_id, request_id,
                     action_code, entity_type, entity_id, entity_no, before_status, after_status,
                     operation_summary, changed_fields_summary, result, error_code, error_reason,
                     idempotency_key_hash, occurred_at, created_at, updated_at, isdel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, entry.id(), entry.tenantId(), entry.actorType(), entry.actorId(), entry.actorAccount(),
                entry.sessionId(), entry.requestId(), entry.actionCode(), entry.entityType(), entry.entityId(),
                entry.entityNo(), entry.beforeStatus(), entry.afterStatus(), entry.operationSummary(),
                entry.changedFieldsSummary(), entry.result().name(), entry.errorCode(), entry.errorReason(),
                entry.idempotencyKeyHash(), entry.occurredAt(), entry.occurredAt(), entry.occurredAt());
    }

    @Override
    public List<OperationAuditEntry> find(OperationAuditQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, actor_type, actor_id, actor_account, session_id, request_id,
                       action_code, entity_type, entity_id, entity_no, before_status, after_status,
                       operation_summary, changed_fields_summary, result, error_code, error_reason,
                       idempotency_key_hash, occurred_at
                  FROM core_operation_audit_log
                 WHERE tenant_id = ? AND entity_type = ? AND entity_id = ? AND isdel = 0
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(query.tenantId());
        parameters.add(query.entityType());
        parameters.add(query.entityId());
        if (query.occurredFrom() != null) {
            sql.append(" AND occurred_at >= ?");
            parameters.add(query.occurredFrom());
        }
        if (query.occurredTo() != null) {
            sql.append(" AND occurred_at <= ?");
            parameters.add(query.occurredTo());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        parameters.add(query.limit());
        return jdbcTemplate.query(sql.toString(), this::mapRow, parameters.toArray());
    }

    private OperationAuditEntry mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new OperationAuditEntry(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getString("actor_type"),
                resultSet.getObject("actor_id", UUID.class), resultSet.getString("actor_account"),
                resultSet.getString("session_id"), resultSet.getString("request_id"),
                resultSet.getString("action_code"), resultSet.getString("entity_type"),
                resultSet.getObject("entity_id", UUID.class), resultSet.getString("entity_no"),
                resultSet.getString("before_status"), resultSet.getString("after_status"),
                resultSet.getString("operation_summary"), resultSet.getString("changed_fields_summary"),
                OperationAuditResult.valueOf(resultSet.getString("result")), resultSet.getString("error_code"),
                resultSet.getString("error_reason"), resultSet.getString("idempotency_key_hash"),
                resultSet.getObject("occurred_at", OffsetDateTime.class));
    }
}
