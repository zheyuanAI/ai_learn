package com.ailearn.platform.iot.contextlink.infrastructure;

import com.ailearn.platform.iot.contextlink.domain.AlarmContextCandidate;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkTask;
import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.ailearn.platform.iot.contextlink.domain.port.AlarmContextLinkRepository;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 告警上下文补链 PostgreSQL 适配器。
 * 只访问 IoT V2 已有的告警字段和 iot_alarm_context_task，不创建或修改迁移表结构。
 */
@Repository
public class PostgresAlarmContextLinkRepository implements AlarmContextLinkRepository {
    private final JdbcTemplate jdbc;

    public PostgresAlarmContextLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AlarmContextCandidate> findAlarm(UUID tenantId, UUID alarmId) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_id, triggered_at, context_source, context_status,
                       operation_execution_id, work_order_id
                  FROM iot_device_alarm
                 WHERE tenant_id = ? AND id = ?
                """, this::alarmRow, tenantId, alarmId).stream().findFirst());
    }

    @Override
    public void enqueue(UUID tenantId, UUID alarmId, OffsetDateTime nextRetryAt) {
        db(() -> {
            // 依赖 V2 的部分唯一索引，以 UPSERT 消除“查询后插入”的并发重复任务窗口。
            jdbc.update("""
                    INSERT INTO iot_alarm_context_task
                        (id, tenant_id, alarm_id, status, retry_count, next_retry_at, created_at, updated_at)
                    VALUES (?, ?, ?, 'Pending', 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (tenant_id, alarm_id) WHERE status <> 'Completed'
                    DO UPDATE SET status = CASE
                                             WHEN iot_alarm_context_task.status = 'Processing'
                                             THEN iot_alarm_context_task.status ELSE 'Pending' END,
                                  next_retry_at = EXCLUDED.next_retry_at,
                                  updated_at = CURRENT_TIMESTAMP
                    """, UUID.randomUUID(), tenantId, alarmId, nextRetryAt);
            return null;
        });
    }

    @Override
    public Optional<ContextLinkTask> claimDue(UUID tenantId, UUID alarmId, OffsetDateTime now) {
        return claim(" AND alarm_id = ?", tenantId, now, alarmId);
    }

    @Override
    public Optional<ContextLinkTask> claimNextDue(UUID tenantId, OffsetDateTime now) {
        return claim("", tenantId, now);
    }

    private Optional<ContextLinkTask> claim(String suffix, UUID tenantId, OffsetDateTime now, Object... suffixArgs) {
        return db(() -> {
            Object[] selectArgs = new Object[2 + suffixArgs.length];
            selectArgs[0] = tenantId;
            selectArgs[1] = now;
            System.arraycopy(suffixArgs, 0, selectArgs, 2, suffixArgs.length);
            List<ContextLinkTask> tasks = jdbc.query("""
                    SELECT id, tenant_id, alarm_id, status, retry_count, next_retry_at
                      FROM iot_alarm_context_task
                     WHERE tenant_id = ?
                       AND status IN ('Pending', 'Retry')
                       AND (next_retry_at IS NULL OR next_retry_at <= ?)
                    """ + suffix + " ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED",
                    this::taskRow, selectArgs);
            if (tasks.isEmpty()) {
                return Optional.empty();
            }
            ContextLinkTask task = tasks.get(0);
            int changed = jdbc.update("""
                    UPDATE iot_alarm_context_task
                       SET status = 'Processing', updated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND id = ? AND status IN ('Pending', 'Retry')
                    """, tenantId, task.id());
            return changed == 1 ? Optional.of(new ContextLinkTask(task.id(), task.tenantId(), task.alarmId(),
                    "Processing", task.retryCount(), task.nextRetryAt())) : Optional.empty();
        });
    }

    @Override
    public boolean linkAutomatically(UUID tenantId, UUID alarmId, ProductionContextView context,
                                     OffsetDateTime linkedAt) {
        return db(() -> jdbc.update("""
                UPDATE iot_device_alarm
                   SET operation_execution_id = ?, work_order_id = ?,
                       context_source = 'Automatic', context_status = 'Linked',
                       updated_at = ?, updated_by = NULL
                 WHERE tenant_id = ? AND id = ? AND context_status = 'Pending'
                   AND operation_execution_id IS NULL AND work_order_id IS NULL
                """, context.operationExecutionId(), context.workOrderId(), linkedAt, tenantId, alarmId) == 1);
    }

    @Override
    public boolean linkManually(UUID tenantId, UUID alarmId, UUID operationExecutionId,
                                UUID workOrderId, OffsetDateTime linkedAt) {
        return db(() -> jdbc.update("""
                UPDATE iot_device_alarm
                   SET operation_execution_id = ?, work_order_id = ?,
                       context_source = 'Manual', context_status = 'Linked',
                       updated_at = ?, updated_by = NULL
                 WHERE tenant_id = ? AND id = ? AND context_status <> 'Linked'
                """, operationExecutionId, workOrderId, linkedAt, tenantId, alarmId) == 1);
    }

    @Override
    public void markCompleted(UUID tenantId, UUID taskId, OffsetDateTime completedAt) {
        db(() -> jdbc.update("""
                UPDATE iot_alarm_context_task
                   SET status = 'Completed', next_retry_at = NULL, last_error = NULL, updated_at = ?
                 WHERE tenant_id = ? AND id = ?
                """, completedAt, tenantId, taskId));
    }

    @Override
    public void markRetry(UUID tenantId, UUID taskId, int retryCount, OffsetDateTime nextRetryAt,
                          String error, OffsetDateTime updatedAt) {
        db(() -> jdbc.update("""
                UPDATE iot_alarm_context_task
                   SET status = 'Retry', retry_count = ?, next_retry_at = ?, last_error = ?, updated_at = ?
                 WHERE tenant_id = ? AND id = ?
                """, retryCount, nextRetryAt, error, updatedAt, tenantId, taskId));
    }

    private AlarmContextCandidate alarmRow(ResultSet rs, int row) throws SQLException {
        return new AlarmContextCandidate(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("device_id", UUID.class), rs.getObject("triggered_at", OffsetDateTime.class),
                rs.getString("context_source"), rs.getString("context_status"),
                rs.getObject("operation_execution_id", UUID.class), rs.getObject("work_order_id", UUID.class));
    }

    private ContextLinkTask taskRow(ResultSet rs, int row) throws SQLException {
        return new ContextLinkTask(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("alarm_id", UUID.class), rs.getString("status"), rs.getInt("retry_count"),
                rs.getObject("next_retry_at", OffsetDateTime.class));
    }

    private <T> T db(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("IoT 告警上下文任务数据库暂时不可用", exception);
        }
    }
}
