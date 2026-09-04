package com.ailearn.platform.iot.telemetry.infrastructure;

import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryDeduplicationClaim;
import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import com.ailearn.platform.iot.telemetry.domain.TelemetryMessageKey;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryDeduplicationPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryFactPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryQueryPort;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * IoT 遥测 PostgreSQL 适配器。
 * <p>
 * 用途：把已通过应用层完整校验的消息去重、遥测事实和设备状态接入 IoT V2 表；所有 SQL 都显式携带租户与设备边界。
 * 流程：去重声明依赖数据库唯一键，事实按消息追加，状态使用设备采集时间的单调 UPSERT。
 * </p>
 * <p>
 * 生产调用应由 {@code TelemetryIngestionServiceImpl} 的事务包住 claim、append、complete 和状态更新；
 * 内存实现仍只用于 focused 测试。
 * </p>
 */
@Repository
public class PostgresTelemetryStore implements TelemetryDeduplicationPort, TelemetryFactPort, DeviceStatusPort,
        TelemetryQueryPort {

    private final JdbcTemplate jdbc;

    /**
     * 用途：注入当前 IoT 服务的数据访问入口；入参为 Spring JDBC 模板。
     */
    public PostgresTelemetryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 用途：原子声明租户内设备消息键；出参为新消息、重复消息或载荷冲突。
     * 流程：先由唯一约束竞争插入，再读取已存在事实 ID；并发事务由数据库锁等待保证只产生一个 NEW。
     */
    @Override
    public TelemetryDeduplicationClaim claim(TelemetryMessageKey key, String payloadHash,
                                              OffsetDateTime receivedAt) {
        validateKey(key, payloadHash, receivedAt);
        return database(() -> {
            int inserted = jdbc.update("""
                    INSERT INTO iot_message_dedup
                        (id, tenant_id, device_id, message_key, message_id, sequence_no,
                         payload_hash, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, device_id, message_key) DO NOTHING
                    """, UUID.randomUUID(), key.tenantId(), key.deviceId(), key.asText(),
                    messageId(key), sequence(key), payloadHash, receivedAt);
            if (inserted == 1) {
                return new TelemetryDeduplicationClaim(TelemetryDeduplicationClaim.Decision.NEW,
                        key, payloadHash, List.of());
            }
            ExistingDeduplication existing = findExisting(key).orElseThrow(() ->
                    new ServiceUnavailableException("IoT 消息去重记录竞争后不可读取"));
            if (!existing.payloadHash().equals(payloadHash)) {
                return new TelemetryDeduplicationClaim(TelemetryDeduplicationClaim.Decision.CONFLICT,
                        key, existing.payloadHash(), existing.telemetryIds());
            }
            return new TelemetryDeduplicationClaim(TelemetryDeduplicationClaim.Decision.DUPLICATE,
                    key, existing.payloadHash(), existing.telemetryIds());
        });
    }

    /**
     * 用途：确认本次消息的全部遥测事实已追加；入参为消息键和事实 ID；不产生新的业务事实。
     * 流程：验证去重记录存在，并核对同租户/设备/消息键下的事实数量和 ID，防止静默完成部分写入。
     */
    @Override
    public void complete(TelemetryMessageKey key, List<UUID> telemetryIds) {
        validateKey(key, null, null);
        if (telemetryIds == null || telemetryIds.isEmpty() || telemetryIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("遥测事实 ID 不能为空");
        }
        database(() -> {
            if (findExisting(key).isEmpty()) {
                throw new IllegalStateException("消息去重记录不存在");
            }
            String placeholders = String.join(",", telemetryIds.stream().map(id -> "?").toList());
            List<Object> args = new ArrayList<>();
            args.add(key.tenantId());
            args.add(key.deviceId());
            args.add(key.asText());
            args.addAll(telemetryIds);
            Long count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                      FROM iot_device_telemetry
                     WHERE tenant_id = ? AND device_id = ? AND message_key = ?
                       AND id IN (""" + placeholders + ")", Long.class, args.toArray());
            if (count == null || count != telemetryIds.size()) {
                throw new IllegalStateException("遥测事实未完整追加");
            }
            return null;
        });
    }

    /**
     * 用途：追加一条消息的全部指标事实；出参按指标编码稳定排序的事实 ID，供重复消息复用。
     */
    @Override
    public List<UUID> append(List<TelemetryFact> facts) {
        if (facts == null || facts.isEmpty()) {
            throw new IllegalArgumentException("遥测事实不能为空");
        }
        List<TelemetryFact> ordered = facts.stream()
                .peek(this::validateFact)
                .sorted(Comparator.comparing(TelemetryFact::metricCode))
                .toList();
        return database(() -> {
            jdbc.batchUpdate("""
                    INSERT INTO iot_device_telemetry
                        (id, tenant_id, device_id, message_key, message_id, sequence_no,
                         ts, received_at, metric_code, metric_value, metric_unit)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, ordered, ordered.size(), (statement, fact) -> {
                statement.setObject(1, fact.id());
                statement.setObject(2, fact.tenantId());
                statement.setObject(3, fact.deviceId());
                statement.setString(4, fact.messageKey());
                statement.setString(5, fact.messageId());
                statement.setObject(6, fact.sequence());
                statement.setObject(7, fact.timestamp());
                statement.setObject(8, fact.receivedAt());
                statement.setString(9, fact.metricCode());
                statement.setString(10, fact.metricValue());
                statement.setString(11, fact.metricUnit());
            });
            return ordered.stream().map(TelemetryFact::id).toList();
        });
    }

    /**
     * 用途：查询当前租户设备的原始遥测事实；入参为可信范围与可选筛选；出参按设备采集时间倒序排列。
     */
    @Override
    public List<TelemetryFact> findFacts(UUID tenantId, UUID deviceId, String metricCode,
                                         OffsetDateTime from, OffsetDateTime to, int limit) {
        requireScope(tenantId, deviceId);
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit 必须在 1 到 200 之间");
        }
        return database(() -> {
            StringBuilder sql = new StringBuilder("""
                    SELECT id, tenant_id, device_id, message_key, message_id, sequence_no,
                           ts, received_at, metric_code, metric_value, metric_unit
                      FROM iot_device_telemetry
                     WHERE tenant_id = ? AND device_id = ?
                    """);
            List<Object> args = new ArrayList<>();
            args.add(tenantId);
            args.add(deviceId);
            if (metricCode != null && !metricCode.isBlank()) {
                sql.append(" AND metric_code = ?");
                args.add(metricCode.trim());
            }
            if (from != null) {
                sql.append(" AND ts >= ?");
                args.add(from);
            }
            if (to != null) {
                sql.append(" AND ts <= ?");
                args.add(to);
            }
            sql.append(" ORDER BY ts DESC, id DESC LIMIT ?");
            args.add(limit);
            return jdbc.query(sql.toString(), this::factRow, args.toArray());
        });
    }

    /**
     * 用途：读取租户内设备状态；不存在时返回初始快照。
     */
    @Override
    public DeviceStatus find(UUID tenantId, UUID deviceId) {
        requireScope(tenantId, deviceId);
        return database(() -> jdbc.query("""
                SELECT tenant_id, device_id, online_status, running_status, alarm_status,
                       last_seen_at, last_message_key, last_source_at
                  FROM iot_device_status
                 WHERE tenant_id = ? AND device_id = ?
                """, this::statusRow, tenantId, deviceId).stream().findFirst()
                .orElseGet(() -> DeviceStatus.initial(tenantId, deviceId)));
    }

    /**
     * 用途：按设备采集时间单调推进状态；迟到消息只保留事实，不覆盖较新状态。
     */
    @Override
    public StatusUpdateResult updateIfNewer(DeviceStatus candidate) {
        if (candidate == null || candidate.sourceTimestamp() == null || candidate.lastSeenAt() == null) {
            throw new IllegalArgumentException("设备状态时间不能为空");
        }
        requireScope(candidate.tenantId(), candidate.deviceId());
        return database(() -> {
            int changed = jdbc.update("""
                    INSERT INTO iot_device_status
                        (id, tenant_id, device_id, online_status, running_status, alarm_status,
                         last_seen_at, last_message_key, last_source_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, device_id) DO UPDATE
                       SET online_status = EXCLUDED.online_status,
                           running_status = EXCLUDED.running_status,
                           alarm_status = EXCLUDED.alarm_status,
                           last_seen_at = EXCLUDED.last_seen_at,
                           last_message_key = EXCLUDED.last_message_key,
                           last_source_at = EXCLUDED.last_source_at,
                           updated_at = EXCLUDED.updated_at
                     WHERE iot_device_status.last_source_at IS NULL
                        OR EXCLUDED.last_source_at > iot_device_status.last_source_at
                    """, UUID.randomUUID(), candidate.tenantId(), candidate.deviceId(), candidate.onlineStatus(),
                    candidate.runningStatus(), candidate.alarmStatus(), candidate.lastSeenAt(),
                    candidate.lastMessageKey(), candidate.sourceTimestamp(), candidate.lastSeenAt());
            DeviceStatus current = find(candidate.tenantId(), candidate.deviceId());
            return new StatusUpdateResult(changed == 1, current);
        });
    }

    /** 按设备模型配置批量扫描超时设备；只更新仍为 Online 的行，避免覆盖新一轮上线状态。 */
    @Override
    public int markOfflineIfTimedOut(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("扫描时间不能为空");
        }
        return database(() -> jdbc.update("""
                UPDATE iot_device_status AS status
                   SET online_status = 'Offline', updated_at = ?
                  FROM iot_device AS device
                  JOIN iot_device_profile AS profile
                    ON profile.tenant_id = device.tenant_id
                   AND profile.id = device.device_profile_id
                   AND profile.isdel = 0
                 WHERE status.tenant_id = device.tenant_id
                   AND status.device_id = device.id
                   AND status.online_status = 'Online'
                   AND status.last_seen_at IS NOT NULL
                   AND status.last_seen_at <= ? - (profile.offline_timeout_seconds * INTERVAL '1 second')
                   AND device.isdel = 0
                """, now, now));
    }

    /** 更新设备告警快照；不触碰遥测事实和设备时间单调字段。 */
    @Override
    public void updateAlarmStatus(UUID tenantId, UUID deviceId, String alarmStatus) {
        requireScope(tenantId, deviceId);
        if (alarmStatus == null || alarmStatus.isBlank()) {
            throw new IllegalArgumentException("设备告警状态不能为空");
        }
        database(() -> {
            jdbc.update("""
                    UPDATE iot_device_status
                       SET alarm_status = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND device_id = ?
                    """, alarmStatus, tenantId, deviceId);
            return null;
        });
    }

    private Optional<ExistingDeduplication> findExisting(TelemetryMessageKey key) {
        List<String> hashes = jdbc.query("""
                SELECT payload_hash
                  FROM iot_message_dedup
                 WHERE tenant_id = ? AND device_id = ? AND message_key = ?
                """, (rs, row) -> rs.getString("payload_hash"),
                key.tenantId(), key.deviceId(), key.asText());
        return hashes.stream().findFirst().map(hash -> new ExistingDeduplication(hash, telemetryIds(key)));
    }

    private List<UUID> telemetryIds(TelemetryMessageKey key) {
        return jdbc.query("""
                SELECT id
                  FROM iot_device_telemetry
                 WHERE tenant_id = ? AND device_id = ? AND message_key = ?
                 ORDER BY metric_code
                """, (rs, row) -> rs.getObject("id", UUID.class),
                key.tenantId(), key.deviceId(), key.asText());
    }

    private DeviceStatus statusRow(ResultSet rs, int row) throws SQLException {
        return new DeviceStatus(rs.getObject("tenant_id", UUID.class), rs.getObject("device_id", UUID.class),
                rs.getString("online_status"), rs.getString("running_status"), rs.getString("alarm_status"),
                rs.getObject("last_seen_at", OffsetDateTime.class), rs.getString("last_message_key"),
                rs.getObject("last_source_at", OffsetDateTime.class));
    }

    private TelemetryFact factRow(ResultSet rs, int row) throws SQLException {
        return new TelemetryFact(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("device_id", UUID.class), rs.getString("message_key"), rs.getString("message_id"),
                rs.getObject("sequence_no", Long.class), rs.getObject("ts", OffsetDateTime.class),
                rs.getObject("received_at", OffsetDateTime.class), rs.getString("metric_code"),
                rs.getString("metric_value"), rs.getString("metric_unit"));
    }

    private void validateKey(TelemetryMessageKey key, String payloadHash, OffsetDateTime receivedAt) {
        requireScope(key == null ? null : key.tenantId(), key == null ? null : key.deviceId());
        if (key == null || key.keyType() == null || key.value() == null || key.value().isBlank()) {
            throw new IllegalArgumentException("遥测消息键不能为空");
        }
        if (payloadHash != null && !payloadHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("遥测载荷摘要格式不正确");
        }
        if (receivedAt == null && payloadHash != null) {
            throw new IllegalArgumentException("遥测接收时间不能为空");
        }
    }

    private void validateFact(TelemetryFact fact) {
        if (fact == null || fact.id() == null || fact.tenantId() == null || fact.deviceId() == null
                || fact.messageKey() == null || fact.messageKey().isBlank() || fact.timestamp() == null
                || fact.receivedAt() == null || fact.metricCode() == null || fact.metricCode().isBlank()
                || fact.metricValue() == null) {
            throw new IllegalArgumentException("遥测事实字段不能为空");
        }
    }

    private void requireScope(UUID tenantId, UUID deviceId) {
        if (tenantId == null || deviceId == null) {
            throw new IllegalArgumentException("租户和设备不能为空");
        }
    }

    private String messageId(TelemetryMessageKey key) {
        return key.messageId();
    }

    private Long sequence(TelemetryMessageKey key) {
        return key.sequenceNo();
    }

    private <T> T database(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("IoT 遥测数据库暂时不可用", exception);
        }
    }

    private record ExistingDeduplication(String payloadHash, List<UUID> telemetryIds) {
    }
}
