package com.ailearn.platform.iot.alarm.infrastructure;

import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.domain.port.AlarmRepository;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * IoT 告警事实 PostgreSQL 适配器；所有 SQL 均带 tenant_id，生命周期更新使用状态 CAS。
 */
@Repository
public class PostgresAlarmRepository implements AlarmRepository {
    private final JdbcTemplate jdbc;

    public PostgresAlarmRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AlarmFact createIfAbsent(AlarmFact fact) {
        return db(() -> {
            jdbc.update("""
                    INSERT INTO iot_device_alarm
                        (id, tenant_id, alarm_no, device_id, rule_id, alarm_type, alarm_level, status,
                         triggered_at, acked_at, ack_user_id, recovered_at, operation_execution_id,
                         work_order_id, context_source, context_status, created_at, ack_comment, updated_at, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, device_id, rule_id)
                        WHERE status IN ('Triggered', 'Acked', 'RecoveredUnacked') DO NOTHING
                    """, fact.id(), fact.tenantId(), fact.alarmNo(), fact.deviceId(), fact.ruleId(), fact.alarmType(),
                    fact.alarmLevel(), fact.status().name(), fact.triggeredAt(), fact.ackedAt(), fact.ackUserId(),
                    fact.recoveredAt(), fact.operationExecutionId(), fact.workOrderId(), fact.contextSource(),
                    fact.contextStatus(), fact.createdAt(), fact.ackComment(), fact.updatedAt(), fact.updatedBy());
            return findActive(fact.tenantId(), fact.deviceId(), fact.ruleId()).orElse(fact);
        });
    }

    @Override
    public Optional<AlarmFact> findById(UUID tenantId, UUID alarmId) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, alarm_no, device_id, rule_id, alarm_type, alarm_level, status,
                       triggered_at, acked_at, ack_user_id, recovered_at, operation_execution_id,
                       work_order_id, context_source, context_status, created_at, ack_comment, updated_at, updated_by
                  FROM iot_device_alarm
                 WHERE tenant_id = ? AND id = ?
                """, this::row, tenantId, alarmId).stream().findFirst());
    }

    @Override
    public Optional<AlarmFact> findActive(UUID tenantId, UUID deviceId, UUID ruleId) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, alarm_no, device_id, rule_id, alarm_type, alarm_level, status,
                       triggered_at, acked_at, ack_user_id, recovered_at, operation_execution_id,
                       work_order_id, context_source, context_status, created_at, ack_comment, updated_at, updated_by
                  FROM iot_device_alarm
                 WHERE tenant_id = ? AND device_id = ? AND rule_id = ?
                   AND status IN ('Triggered', 'Acked', 'RecoveredUnacked')
                 ORDER BY triggered_at DESC LIMIT 1
                """, this::row, tenantId, deviceId, ruleId).stream().findFirst());
    }

    @Override
    public Optional<AlarmFact> transition(UUID tenantId, UUID alarmId, AlarmStatus expected,
                                           AlarmStatus target, OffsetDateTime at, UUID userId) {
        return transition(tenantId, alarmId, expected, target, at, userId, null);
    }

    @Override
    public Optional<AlarmFact> transition(UUID tenantId, UUID alarmId, AlarmStatus expected,
                                           AlarmStatus target, OffsetDateTime at, UUID userId,
                                           String ackComment) {
        return db(() -> {
            int changed = jdbc.update("""
                    UPDATE iot_device_alarm
                       SET status = ?,
                           acked_at = CASE WHEN CAST(? AS UUID) IS NULL THEN acked_at ELSE ? END,
                           ack_user_id = CASE WHEN CAST(? AS UUID) IS NULL THEN ack_user_id ELSE ? END,
                           ack_comment = CASE WHEN CAST(? AS VARCHAR) IS NULL THEN ack_comment ELSE ? END,
                           updated_at = ?,
                           updated_by = COALESCE(?, updated_by),
                           recovered_at = CASE WHEN ? IN ('RecoveredUnacked', 'Recovered')
                                                    AND CAST(? AS UUID) IS NULL
                                               THEN ? ELSE recovered_at END
                     WHERE tenant_id = ? AND id = ? AND status = ?
                    """, target.name(), userId, at, userId, userId, ackComment, ackComment, at, userId,
                    target.name(), userId, at, tenantId, alarmId, expected.name());
            return changed == 1 ? findById(tenantId, alarmId) : Optional.empty();
        });
    }

    @Override
    public boolean hasActiveForDevice(UUID tenantId, UUID deviceId) {
        return db(() -> Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM iot_device_alarm
                     WHERE tenant_id = ? AND device_id = ?
                       AND status IN ('Triggered', 'Acked', 'RecoveredUnacked')
                )
                """, Boolean.class, tenantId, deviceId)));
    }

    @Override
    public List<AlarmFact> findPage(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
                                    OffsetDateTime from, OffsetDateTime to, String contextStatus,
                                    int offset, int limit) {
        Query query = query(tenantId, deviceId, status, alarmLevel, from, to, contextStatus);
        String sql = query.sql + " ORDER BY triggered_at DESC LIMIT ? OFFSET ?";
        query.args.add(limit);
        query.args.add(offset);
        return db(() -> jdbc.query(sql, this::row, query.args.toArray()));
    }

    @Override
    public long count(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
                      OffsetDateTime from, OffsetDateTime to, String contextStatus) {
        Query query = query(tenantId, deviceId, status, alarmLevel, from, to, contextStatus);
        return db(() -> jdbc.queryForObject("SELECT COUNT(*) FROM iot_device_alarm "
                + query.sql.substring(query.sql.indexOf(" WHERE")), Long.class, query.args.toArray()));
    }

    private Query query(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
                        OffsetDateTime from, OffsetDateTime to, String contextStatus) {
        StringBuilder sql = new StringBuilder(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (deviceId != null) { sql.append(" AND device_id = ?"); args.add(deviceId); }
        if (status != null) { sql.append(" AND status = ?"); args.add(status.name()); }
        if (alarmLevel != null && !alarmLevel.isBlank()) { sql.append(" AND alarm_level = ?"); args.add(alarmLevel); }
        if (from != null) { sql.append(" AND triggered_at >= ?"); args.add(from); }
        if (to != null) { sql.append(" AND triggered_at <= ?"); args.add(to); }
        if (contextStatus != null && !contextStatus.isBlank()) { sql.append(" AND context_status = ?"); args.add(contextStatus); }
        return new Query("FROM iot_device_alarm" + sql, args);
    }

    private AlarmFact row(ResultSet rs, int row) throws SQLException {
        return new AlarmFact(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("alarm_no"), rs.getObject("device_id", UUID.class), rs.getObject("rule_id", UUID.class),
                rs.getString("alarm_type"), rs.getString("alarm_level"), AlarmStatus.valueOf(rs.getString("status")),
                rs.getObject("triggered_at", OffsetDateTime.class), rs.getObject("acked_at", OffsetDateTime.class),
                rs.getObject("ack_user_id", UUID.class), rs.getObject("recovered_at", OffsetDateTime.class),
                rs.getObject("operation_execution_id", UUID.class), rs.getObject("work_order_id", UUID.class),
                rs.getString("context_source"), rs.getString("context_status"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("ack_comment"),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("updated_by", UUID.class));
    }

    private <T> T db(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("IoT 告警事实数据库暂时不可用", exception);
        }
    }

    private static final class Query {
        private final String sql;
        private final List<Object> args;

        private Query(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }
    }
}
