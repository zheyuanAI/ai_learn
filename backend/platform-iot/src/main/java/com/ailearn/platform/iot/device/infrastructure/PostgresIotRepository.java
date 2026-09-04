package com.ailearn.platform.iot.device.infrastructure;

import com.ailearn.platform.iot.credential.domain.CredentialStatus;
import com.ailearn.platform.iot.credential.domain.DeviceCredential;
import com.ailearn.platform.iot.credential.domain.port.CredentialRepository;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
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
 * IoT 三类基础聚合的 PostgreSQL 适配器。
 * 用途：只访问 IoT V2 自有表；所有查询和更新均携带租户条件，设备、模型和凭证不跨服务直查。
 */
@Repository
public class PostgresIotRepository implements DeviceProfileRepository, DeviceRepository, CredentialRepository {
    private final JdbcTemplate jdbc;

    public PostgresIotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsProfileByCode(UUID tenantId, String profileCode) {
        return db(() -> jdbc.queryForObject("SELECT COUNT(*) FROM iot_device_profile WHERE tenant_id = ? AND profile_code = ? AND isdel = 0",
                Long.class, tenantId, profileCode) > 0);
    }

    @Override
    public DeviceProfile insert(DeviceProfile profile) {
        return db(() -> {
            jdbc.update("""
                    INSERT INTO iot_device_profile
                    (id, tenant_id, profile_code, profile_name, status, offline_timeout_seconds,
                     created_by, created_at, updated_by, updated_at, isdel)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, profile.id(), profile.tenantId(), profile.profileCode(), profile.profileName(), profile.status(),
                    profile.offlineTimeoutSeconds(), profile.createdBy(), profile.createdAt(), profile.updatedBy(), profile.updatedAt());
            for (DeviceProfile.MetricDefinition metric : profile.metrics()) {
                jdbc.update("""
                        INSERT INTO iot_device_profile_metric
                        (id, tenant_id, profile_id, metric_code, metric_name, value_type, unit, required,
                         created_by, created_at, updated_by, updated_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """, UUID.randomUUID(), profile.tenantId(), profile.id(), metric.metricCode(), metric.metricName(),
                        metric.valueType().name(), metric.unit(), metric.required(), profile.createdBy(), profile.createdAt(),
                        profile.updatedBy(), profile.updatedAt());
            }
            return profile;
        });
    }

    @Override
    public Optional<DeviceProfile> findProfileById(UUID tenantId, UUID id) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, profile_code, profile_name, status, offline_timeout_seconds,
                       created_by, created_at, updated_by, updated_at
                  FROM iot_device_profile
                 WHERE tenant_id = ? AND id = ? AND isdel = 0
                """, this::profileRow, tenantId, id).stream().findFirst().map(profile -> {
            List<DeviceProfile.MetricDefinition> metrics = jdbc.query("""
                    SELECT metric_code, metric_name, value_type, unit, required
                      FROM iot_device_profile_metric
                     WHERE tenant_id = ? AND profile_id = ? AND isdel = 0
                     ORDER BY metric_code
                    """, (rs, row) -> new DeviceProfile.MetricDefinition(rs.getString("metric_code"),
                    rs.getString("metric_name"), MetricValueType.valueOf(rs.getString("value_type")),
                    rs.getString("unit"), rs.getBoolean("required")), tenantId, id);
            return new DeviceProfile(profile.id(), profile.tenantId(), profile.profileCode(), profile.profileName(),
                    profile.status(), profile.offlineTimeoutSeconds(), metrics, profile.createdBy(), profile.createdAt(),
                    profile.updatedBy(), profile.updatedAt());
        }));
    }

    @Override
    public List<DeviceProfile> findPage(UUID tenantId, String code, int offset, int limit) {
        String suffix = code == null ? "" : " AND profile_code ILIKE ?";
        Object[] args = code == null ? new Object[]{tenantId, limit, offset}
                : new Object[]{tenantId, "%" + code + "%", limit, offset};
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, profile_code, profile_name, status, offline_timeout_seconds,
                       created_by, created_at, updated_by, updated_at
                  FROM iot_device_profile
                 WHERE tenant_id = ? AND isdel = 0
                """ + suffix + " ORDER BY profile_code LIMIT ? OFFSET ?", this::profileRow, args)
                .stream().map(this::loadMetrics).toList());
    }

    @Override
    public long count(UUID tenantId, String code) {
        String suffix = code == null ? "" : " AND profile_code ILIKE ?";
        Object[] args = code == null ? new Object[]{tenantId} : new Object[]{tenantId, "%" + code + "%"};
        return db(() -> jdbc.queryForObject("SELECT COUNT(*) FROM iot_device_profile WHERE tenant_id = ? AND isdel = 0" + suffix,
                Long.class, args));
    }

    @Override
    public boolean existsMetric(UUID tenantId, UUID profileId, String metricCode) {
        return db(() -> jdbc.queryForObject("SELECT COUNT(*) FROM iot_device_profile_metric WHERE tenant_id = ? AND profile_id = ? AND metric_code = ? AND isdel = 0",
                Long.class, tenantId, profileId, metricCode) > 0);
    }

    @Override
    public AlarmRule insertRule(AlarmRule rule) {
        return db(() -> {
            jdbc.update("""
                    INSERT INTO iot_device_alarm_rule
                    (id, tenant_id, rule_code, device_profile_id, device_id, metric_code, operator,
                     trigger_threshold, recovery_threshold, alarm_level, status, created_by, created_at,
                     updated_by, updated_at, isdel)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, rule.id(), rule.tenantId(), rule.ruleCode(), rule.deviceProfileId(), rule.deviceId(), rule.metricCode(),
                    rule.operator(), rule.triggerThreshold(), rule.recoveryThreshold(), rule.alarmLevel(), rule.status(),
                    rule.createdBy(), rule.createdAt(), rule.createdBy(), rule.createdAt());
            return rule;
        });
    }

    @Override
    public boolean existsRuleByCode(UUID tenantId, String ruleCode) {
        return db(() -> jdbc.queryForObject("SELECT COUNT(*) FROM iot_device_alarm_rule WHERE tenant_id = ? AND rule_code = ? AND isdel = 0",
                Long.class, tenantId, ruleCode) > 0);
    }

    @Override
    public List<AlarmRule> findRules(UUID tenantId, UUID profileId, int offset, int limit) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, rule_code, device_profile_id, device_id, metric_code, operator,
                       trigger_threshold, recovery_threshold, alarm_level, status, created_by, created_at
                  FROM iot_device_alarm_rule
                 WHERE tenant_id = ? AND isdel = 0 AND (? IS NULL OR device_profile_id = ?)
                 ORDER BY rule_code LIMIT ? OFFSET ?
                """, this::ruleRow, tenantId, profileId, profileId, limit, offset));
    }

    @Override
    public boolean existsDeviceByCode(UUID tenantId, String deviceCode) {
        return db(() -> jdbc.queryForObject("SELECT COUNT(*) FROM iot_device WHERE tenant_id = ? AND device_code = ? AND isdel = 0",
                Long.class, tenantId, deviceCode) > 0);
    }

    @Override
    public Device insert(Device device) {
        return db(() -> {
            jdbc.update("""
                    INSERT INTO iot_device
                    (id, tenant_id, device_code, device_name, device_profile_id, protocol_type,
                     lifecycle_status, work_center_id, area_id, map_point_id, created_by, created_at,
                     updated_by, updated_at, isdel)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, device.id(), device.tenantId(), device.deviceCode(), device.deviceName(), device.deviceProfileId(),
                    device.protocolType(), device.lifecycleStatus().name(), device.workCenterId(), device.areaId(), device.mapPointId(),
                    device.createdBy(), device.createdAt(), device.updatedBy(), device.updatedAt());
            return device;
        });
    }

    @Override
    public Optional<Device> findDeviceById(UUID tenantId, UUID id) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_code, device_name, device_profile_id, protocol_type,
                       lifecycle_status, work_center_id, area_id, map_point_id, created_by, created_at,
                       updated_by, updated_at
                  FROM iot_device WHERE tenant_id = ? AND id = ? AND isdel = 0
                """, this::deviceRow, tenantId, id).stream().findFirst());
    }

    @Override
    public Optional<Device> findByCode(UUID tenantId, String deviceCode) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_code, device_name, device_profile_id, protocol_type,
                       lifecycle_status, work_center_id, area_id, map_point_id, created_by, created_at,
                       updated_by, updated_at
                  FROM iot_device WHERE tenant_id = ? AND device_code = ? AND isdel = 0
                """, this::deviceRow, tenantId, deviceCode)
                .stream().findFirst());
    }

    @Override
    public List<Device> findPage(UUID tenantId, String code, DeviceLifecycleStatus status, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, device_code, device_name, device_profile_id, protocol_type,
                       lifecycle_status, work_center_id, area_id, map_point_id, created_by, created_at,
                       updated_by, updated_at
                  FROM iot_device WHERE tenant_id = ? AND isdel = 0
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        if (code != null) { sql.append(" AND device_code ILIKE ?"); args.add("%" + code + "%"); }
        if (status != null) { sql.append(" AND lifecycle_status = ?"); args.add(status.name()); }
        sql.append(" ORDER BY device_code LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
        return db(() -> jdbc.query(sql.toString(), this::deviceRow, args.toArray()));
    }

    @Override
    public long count(UUID tenantId, String code, DeviceLifecycleStatus status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM iot_device WHERE tenant_id = ? AND isdel = 0");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        if (code != null) { sql.append(" AND device_code ILIKE ?"); args.add("%" + code + "%"); }
        if (status != null) { sql.append(" AND lifecycle_status = ?"); args.add(status.name()); }
        return db(() -> jdbc.queryForObject(sql.toString(), Long.class, args.toArray()));
    }

    @Override
    public Device updateLifecycle(UUID tenantId, UUID id, DeviceLifecycleStatus expected, DeviceLifecycleStatus target,
                                  UUID operatorId, OffsetDateTime updatedAt) {
        return db(() -> {
            int changed = jdbc.update("""
                    UPDATE iot_device SET lifecycle_status = ?, updated_by = ?, updated_at = ?
                     WHERE tenant_id = ? AND id = ? AND lifecycle_status = ? AND isdel = 0
                    """,
                    target.name(), operatorId, updatedAt, tenantId, id, expected.name());
            return changed == 1 ? findDeviceById(tenantId, id).orElse(null) : null;
        });
    }

    @Override
    public boolean hasHistoricalFacts(UUID tenantId, UUID deviceId) {
        return db(() -> jdbc.queryForObject("""
                SELECT (EXISTS (SELECT 1 FROM iot_device_telemetry WHERE tenant_id = ? AND device_id = ?)
                    OR EXISTS (SELECT 1 FROM iot_device_alarm WHERE tenant_id = ? AND device_id = ?))
                """,
                Boolean.class, tenantId, deviceId, tenantId, deviceId));
    }

    @Override
    public DeviceCredential insert(DeviceCredential credential) {
        return db(() -> {
            jdbc.update("""
                    INSERT INTO iot_device_credential
                    (id, tenant_id, device_id, credential_reference, secret_hash, secret_salt,
                     credential_status, created_by, created_at, isdel)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, credential.id(), credential.tenantId(), credential.deviceId(), credential.credentialReference(),
                    credential.secretHash(), credential.secretSalt(), credential.status().name(), credential.createdBy(), credential.createdAt());
            return credential;
        });
    }

    @Override
    public Optional<DeviceCredential> findById(UUID tenantId, UUID deviceId, UUID credentialId) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_id, credential_reference, secret_hash, secret_salt,
                       credential_status, created_by, created_at, revoked_by, revoked_at
                  FROM iot_device_credential WHERE tenant_id = ? AND device_id = ? AND id = ? AND isdel = 0
                """,
                this::credentialRow, tenantId, deviceId, credentialId).stream().findFirst());
    }

    @Override
    public Optional<DeviceCredential> findByReference(UUID tenantId, String credentialReference) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_id, credential_reference, secret_hash, secret_salt,
                       credential_status, created_by, created_at, revoked_by, revoked_at
                  FROM iot_device_credential WHERE tenant_id = ? AND credential_reference = ? AND isdel = 0
                """,
                this::credentialRow, tenantId, credentialReference).stream().findFirst());
    }

    /**
     * 用途：按 MQTT 主题中的凭证引用读取候选凭证；出参保留所有租户匹配项，供应用层拒绝歧义认证。
     * 说明：此查询不向 HTTP 暴露结果，设备与租户归属仍由 verifyReference 二次校验。
     */
    @Override
    public List<DeviceCredential> findByReferenceAcrossTenants(String credentialReference) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_id, credential_reference, secret_hash, secret_salt,
                       credential_status, created_by, created_at, revoked_by, revoked_at
                  FROM iot_device_credential
                 WHERE credential_reference = ? AND isdel = 0
                """,
                this::credentialRow, credentialReference));
    }

    @Override
    public List<DeviceCredential> findByDevice(UUID tenantId, UUID deviceId) {
        return db(() -> jdbc.query("""
                SELECT id, tenant_id, device_id, credential_reference, secret_hash, secret_salt,
                       credential_status, created_by, created_at, revoked_by, revoked_at
                  FROM iot_device_credential WHERE tenant_id = ? AND device_id = ? AND isdel = 0
                 ORDER BY created_at DESC
                """,
                this::credentialRow, tenantId, deviceId));
    }

    @Override
    public boolean revoke(UUID tenantId, UUID deviceId, UUID credentialId, UUID operatorId, OffsetDateTime revokedAt) {
        return db(() -> jdbc.update("""
                UPDATE iot_device_credential SET credential_status = 'Revoked', revoked_by = ?, revoked_at = ?
                 WHERE tenant_id = ? AND device_id = ? AND id = ? AND credential_status = 'Active' AND isdel = 0
                """,
                operatorId, revokedAt, tenantId, deviceId, credentialId) == 1);
    }

    private DeviceProfile profileRow(ResultSet rs, int row) throws SQLException {
        return new DeviceProfile(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getString("profile_code"), rs.getString("profile_name"), rs.getString("status"),
                rs.getInt("offline_timeout_seconds"), List.of(), rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_by", UUID.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    /**
     * 为分页结果加载设备模型的指标白名单，避免列表接口返回空指标并误导遥测校验方。
     * 入参：已完成基础字段映射的设备模型；出参：包含同租户指标定义的新模型；流程：按模型和租户读取指标后重建不可变记录。
     */
    private DeviceProfile loadMetrics(DeviceProfile profile) {
        List<DeviceProfile.MetricDefinition> metrics = jdbc.query("""
                SELECT metric_code, metric_name, value_type, unit, required
                  FROM iot_device_profile_metric
                 WHERE tenant_id = ? AND profile_id = ? AND isdel = 0
                 ORDER BY metric_code
                """, (rs, row) -> new DeviceProfile.MetricDefinition(rs.getString("metric_code"),
                rs.getString("metric_name"), MetricValueType.valueOf(rs.getString("value_type")),
                rs.getString("unit"), rs.getBoolean("required")), profile.tenantId(), profile.id());
        return new DeviceProfile(profile.id(), profile.tenantId(), profile.profileCode(), profile.profileName(),
                profile.status(), profile.offlineTimeoutSeconds(), metrics, profile.createdBy(), profile.createdAt(),
                profile.updatedBy(), profile.updatedAt());
    }

    private AlarmRule ruleRow(ResultSet rs, int row) throws SQLException {
        return new AlarmRule(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("rule_code"),
                rs.getObject("device_profile_id", UUID.class), rs.getObject("device_id", UUID.class), rs.getString("metric_code"),
                rs.getString("operator"), rs.getBigDecimal("trigger_threshold"), rs.getBigDecimal("recovery_threshold"),
                rs.getString("alarm_level"), rs.getString("status"), rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private Device deviceRow(ResultSet rs, int row) throws SQLException {
        return new Device(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("device_code"),
                rs.getString("device_name"), rs.getObject("device_profile_id", UUID.class), rs.getString("protocol_type"),
                DeviceLifecycleStatus.parse(rs.getString("lifecycle_status")), rs.getObject("work_center_id", UUID.class),
                rs.getObject("area_id", UUID.class), rs.getObject("map_point_id", UUID.class), rs.getObject("created_by", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_by", UUID.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private DeviceCredential credentialRow(ResultSet rs, int row) throws SQLException {
        return new DeviceCredential(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("device_id", UUID.class), rs.getString("credential_reference"), rs.getString("secret_hash"),
                rs.getString("secret_salt"), CredentialStatus.valueOf(rs.getString("credential_status")),
                rs.getObject("created_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("revoked_by", UUID.class), rs.getObject("revoked_at", OffsetDateTime.class));
    }

    private <T> T db(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("IoT 数据库暂时不可用", exception);
        }
    }
}
