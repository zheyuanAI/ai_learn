package com.ailearn.platform.iot.alarm.application;

import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.domain.port.AlarmRepository;
import com.ailearn.platform.iot.alarm.domain.port.AlarmRuleFactsPort;
import com.ailearn.platform.iot.alarm.dto.AlarmPageResult;
import com.ailearn.platform.iot.alarm.dto.AlarmView;
import com.ailearn.platform.iot.alarm.exception.AlarmErrorCode;
import com.ailearn.platform.iot.alarm.exception.AlarmException;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryMetric;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryAlarmPort;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * IoT 告警生命周期服务。
 * 用途：消费已保存遥测，按单指标阈值与恢复阈值推进本地告警事实；人工确认使用共享幂等记录。
 * 关键边界：此服务不调用 Core，因此 Core 暂不可用不会阻断 IoT 遥测和告警事实保存。
 */
@Service
@Validated
public class AlarmApplicationServiceImpl implements AlarmApplicationService, TelemetryAlarmPort {
    private static final String ACK_OPERATION = "iot:alarm:ack";
    private final AlarmRepository alarmRepository;
    private final AlarmRuleFactsPort ruleFactsPort;
    private final IotIdempotencyExecutor idempotency;
    private final DeviceStatusPort statusPort;

    public AlarmApplicationServiceImpl(AlarmRepository alarmRepository, AlarmRuleFactsPort ruleFactsPort,
                                       IotIdempotencyExecutor idempotency) {
        this(alarmRepository, ruleFactsPort, idempotency, null);
    }

    /** 生产装配构造器：告警生命周期变化后同步设备状态快照。 */
    @org.springframework.beans.factory.annotation.Autowired
    public AlarmApplicationServiceImpl(AlarmRepository alarmRepository, AlarmRuleFactsPort ruleFactsPort,
                                       IotIdempotencyExecutor idempotency, DeviceStatusPort statusPort) {
        this.alarmRepository = alarmRepository;
        this.ruleFactsPort = ruleFactsPort;
        this.idempotency = idempotency;
        this.statusPort = statusPort;
    }

    /**
     * 用途：接收遥测服务已确认保存且较新的消息；入参为设备消息和最新状态；无返回值。
     * 流程：读取当前租户规则 -> 数值阈值判断 -> 原子创建活动告警或 CAS 推进恢复状态。
     */
    @Override
    public void onTelemetryAccepted(TelemetryIngestionCommand command, DeviceStatus status) {
        if (command == null || command.credentialContext() == null || command.deviceId() == null) {
            return;
        }
        UUID tenantId = command.credentialContext().tenantId();
        if (tenantId == null) {
            return;
        }
        List<AlarmRule> rules = ruleFactsPort.findActiveRules(tenantId, command.deviceId());
        for (AlarmRule rule : rules) {
            if (rule == null || rule.id() == null || rule.metricCode() == null || !tenantId.equals(rule.tenantId())) {
                continue;
            }
            metric(command, rule.metricCode()).ifPresent(value -> processRule(command, rule, value));
        }
        updateDeviceAlarmStatus(tenantId, command.deviceId());
    }

    private void processRule(TelemetryIngestionCommand command, AlarmRule rule, BigDecimal value) {
        UUID tenantId = command.credentialContext().tenantId();
        Optional<AlarmFact> active = alarmRepository.findActive(tenantId, command.deviceId(), rule.id());
        if (active.isPresent()) {
            AlarmFact current = active.get();
            if (current.status() == AlarmStatus.Triggered && recoveryMatches(rule, value)) {
                alarmRepository.transition(tenantId, current.id(), AlarmStatus.Triggered,
                        AlarmStatus.RecoveredUnacked, command.timestamp(), null);
            } else if (current.status() == AlarmStatus.Acked && recoveryMatches(rule, value)) {
                alarmRepository.transition(tenantId, current.id(), AlarmStatus.Acked,
                        AlarmStatus.Recovered, command.timestamp(), null);
            }
            return;
        }
        if (triggerMatches(rule, value)) {
            UUID alarmId = UUID.randomUUID();
            AlarmFact fact = new AlarmFact(alarmId, tenantId, "ALM-" + alarmId,
                    command.deviceId(), rule.id(), rule.ruleCode(), rule.alarmLevel(), AlarmStatus.Triggered,
                    command.timestamp(), null, null, null, null, null, null, "Pending", command.receivedAt());
            alarmRepository.createIfAbsent(fact);
        }
    }

    private java.util.Optional<BigDecimal> metric(TelemetryIngestionCommand command, String metricCode) {
        for (TelemetryMetric item : command.metrics()) {
            if (item == null || !metricCode.equals(item.metricCode()) || item.metricValue() == null) {
                continue;
            }
            try {
                return java.util.Optional.of(new BigDecimal(item.metricValue().toString()));
            } catch (RuntimeException ignored) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private boolean triggerMatches(AlarmRule rule, BigDecimal value) {
        return compare(value, rule.triggerThreshold(), rule.operator());
    }

    private boolean recoveryMatches(AlarmRule rule, BigDecimal value) {
        if (rule.recoveryThreshold() == null) {
            return false;
        }
        return switch (rule.operator().toUpperCase(java.util.Locale.ROOT)) {
            case "GT", "GTE" -> value.compareTo(rule.recoveryThreshold()) <= 0;
            case "LT", "LTE" -> value.compareTo(rule.recoveryThreshold()) >= 0;
            case "EQ" -> value.compareTo(rule.recoveryThreshold()) != 0;
            default -> false;
        };
    }

    private boolean compare(BigDecimal value, BigDecimal threshold, String operator) {
        if (threshold == null || operator == null) {
            return false;
        }
        return switch (operator.toUpperCase(java.util.Locale.ROOT)) {
            case "GT" -> value.compareTo(threshold) > 0;
            case "GTE" -> value.compareTo(threshold) >= 0;
            case "LT" -> value.compareTo(threshold) < 0;
            case "LTE" -> value.compareTo(threshold) <= 0;
            case "EQ" -> value.compareTo(threshold) == 0;
            default -> false;
        };
    }

    @Override
    @PreAuthorize("hasAuthority('iot:alarm:view')")
    public AlarmPageResult page(UUID deviceId, AlarmStatus status, String alarmLevel, OffsetDateTime from,
                                OffsetDateTime to, String contextStatus, int page, int size) {
        validatePage(page, size);
        UUID tenantId = TenantContextHolder.requireTenantId();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("date_from 不能晚于 date_to");
        }
        int offset = (page - 1) * size;
        List<AlarmView> records = alarmRepository.findPage(tenantId, deviceId, status, normalize(alarmLevel),
                        from, to, normalize(contextStatus), offset, size).stream()
                .map(AlarmView::from).toList();
        long total = alarmRepository.count(tenantId, deviceId, status, normalize(alarmLevel), from, to,
                normalize(contextStatus));
        return new AlarmPageResult(records, total, page, size);
    }

    @Override
    @PreAuthorize("hasAuthority('iot:alarm:view')")
    public AlarmView detail(UUID alarmId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return alarmRepository.findById(tenantId, alarmId)
                .map(AlarmView::from)
                .orElseThrow(() -> new IotException(IotErrorCode.TENANT_VIOLATION, "告警不存在或不属于当前租户"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('iot:alarm:ack')")
    public AlarmView ack(UUID alarmId, String ackComment, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key 必须为 1 到 128 个字符");
        }
        String comment = normalizeComment(ackComment);
        UUID tenantId = TenantContextHolder.requireTenantId();
        String hash = sha256(alarmId + "\n" + comment);
        return idempotency.execute(ACK_OPERATION, tenantId, idempotencyKey.trim(), hash, AlarmView.class,
                () -> doAck(tenantId, alarmId, comment));
    }

    private AlarmView doAck(UUID tenantId, UUID alarmId, String comment) {
        UserContextHolder.requireUserId();
        AlarmFact current = alarmRepository.findById(tenantId, alarmId)
                .orElseThrow(() -> new IotException(IotErrorCode.TENANT_VIOLATION, "告警不存在或不属于当前租户"));
        AlarmStatus target = switch (current.status()) {
            case Triggered -> AlarmStatus.Acked;
            case RecoveredUnacked -> AlarmStatus.Recovered;
            case Acked, Recovered -> throw new AlarmException(AlarmErrorCode.STATE_INVALID, "告警已确认或已完成");
        };
        AlarmView result = alarmRepository.transition(tenantId, alarmId, current.status(), target,
                        OffsetDateTime.now(), UserContextHolder.requireUserId(), comment)
                .map(AlarmView::from)
                .orElseThrow(() -> new AlarmException(AlarmErrorCode.STATE_INVALID, "告警状态已被其他请求改变"));
        updateDeviceAlarmStatus(tenantId, current.deviceId());
        return result;
    }

    /** 根据设备当前活动告警集合刷新 DeviceStatus.alarm_status，避免状态快照停留在 Normal。 */
    private void updateDeviceAlarmStatus(UUID tenantId, UUID deviceId) {
        if (statusPort == null) {
            return;
        }
        statusPort.updateAlarmStatus(tenantId, deviceId,
                alarmRepository.hasActiveForDevice(tenantId, deviceId) ? "Alarm" : "Normal");
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank() || comment.trim().length() > 512) {
            throw new IllegalArgumentException("ack_comment 必须为 1 到 512 个字符");
        }
        return comment.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page 必须大于等于 1，size 必须在 1 到 100 之间");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

}
