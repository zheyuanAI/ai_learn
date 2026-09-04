package com.ailearn.platform.iot.telemetry.application;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryDeduplicationClaim;
import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import com.ailearn.platform.iot.telemetry.domain.TelemetryMessageKey;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryAlarmPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryDeduplicationPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryFactPort;
import com.ailearn.platform.iot.telemetry.exception.TelemetryErrorCode;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 遥测摄取应用服务。
 * 用途：统一承接 MQTT 和模拟消息，依赖端口完成租户隔离、整条校验、QoS 1 去重、遥测追加和状态单调推进。
 * 说明：方法同步只保证单个内存服务实例的并发安全，生产适配器仍需依赖数据库唯一键/事务。
 */
@Service
public class TelemetryIngestionServiceImpl implements TelemetryIngestionService {

    private final DeviceRepository deviceRepository;
    private final DeviceProfileRepository profileRepository;
    private final TelemetryDeduplicationPort deduplicationPort;
    private final TelemetryFactPort factPort;
    private final DeviceStatusPort statusPort;
    private final TelemetryAlarmPort alarmPort;

    /**
     * 用途：组装摄取服务的设备、模型、事实、状态和告警边界；入参均为端口，便于替换真实适配器。
     */
    public TelemetryIngestionServiceImpl(DeviceRepository deviceRepository, DeviceProfileRepository profileRepository,
                                         TelemetryDeduplicationPort deduplicationPort, TelemetryFactPort factPort,
                                         DeviceStatusPort statusPort, TelemetryAlarmPort alarmPort) {
        this.deviceRepository = deviceRepository;
        this.profileRepository = profileRepository;
        this.deduplicationPort = deduplicationPort;
        this.factPort = factPort;
        this.statusPort = statusPort;
        this.alarmPort = alarmPort;
    }

    /**
     * 用途：摄取一条遥测消息；入参为可信凭证上下文和设备消息；出参为统一摄取结果。
     * 流程：租户/设备/模型与指标完整校验 -> 原子去重 -> 全量追加 -> 较新消息推进状态 -> 通知告警端口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized TelemetryIngestionResult ingest(TelemetryIngestionCommand command) {
        ValidatedMessage validated = validate(command);
        TelemetryDeduplicationClaim claim = deduplicationPort.claim(validated.key(), validated.payloadHash(),
                command.receivedAt());
        if (claim.decision() == TelemetryDeduplicationClaim.Decision.CONFLICT) {
            throw new TelemetryException(TelemetryErrorCode.PAYLOAD_CONFLICT,
                    "消息键 " + validated.key().asText() + " 已对应其他载荷");
        }
        if (claim.decision() == TelemetryDeduplicationClaim.Decision.DUPLICATE) {
            return new TelemetryIngestionResult(true, true, validated.key().asText(), claim.telemetryIds(),
                    statusPort.find(validated.tenantId(), validated.deviceId()));
        }

        List<TelemetryFact> facts = validated.metrics().stream()
                .map(metric -> new TelemetryFact(UUID.randomUUID(), validated.tenantId(), validated.deviceId(),
                        validated.key().asText(), command.messageId(), command.sequence(), command.timestamp(),
                        command.receivedAt(), metric.metricCode(), metric.metricValue(), metric.metricUnit()))
                .toList();
        List<UUID> telemetryIds = factPort.append(facts);
        deduplicationPort.complete(validated.key(), telemetryIds);

        DeviceStatus current = statusPort.find(validated.tenantId(), validated.deviceId());
        String runningStatus = runningStatus(validated.metrics(), current.runningStatus());
        DeviceStatus candidate = new DeviceStatus(validated.tenantId(), validated.deviceId(), "Online", runningStatus,
                current.alarmStatus(), command.receivedAt(), validated.key().asText(), command.timestamp());
        DeviceStatusPort.StatusUpdateResult update = statusPort.updateIfNewer(candidate);
        if (update.updated()) {
            alarmPort.onTelemetryAccepted(command, update.status());
        }
        return new TelemetryIngestionResult(true, false, validated.key().asText(), telemetryIds, update.status());
    }

    /**
     * 用途：在任何去重或保存动作前完成整条消息校验；出参为规范化消息；非法时不产生任何事实。
     */
    private ValidatedMessage validate(TelemetryIngestionCommand command) {
        if (command == null || command.credentialContext() == null) {
            throw invalid("缺少可信设备凭证上下文");
        }
        TelemetryCredentialContext credential = command.credentialContext();
        if (credential.tenantId() == null || credential.deviceId() == null
                || credential.credentialReference() == null || credential.credentialReference().isBlank()
                || command.deviceId() == null || !credential.deviceId().equals(command.deviceId())) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, "凭证上下文与设备身份不匹配");
        }
        if (command.timestamp() == null || command.receivedAt() == null) {
            throw invalid("ts 和 received_at 不能为空");
        }
        String payloadHash = normalizeHash(command.payloadHash());
        TelemetryMessageKey key = messageKey(credential.tenantId(), command.deviceId(), command.messageId(),
                command.sequence());
        Device device = deviceRepository.findDeviceById(credential.tenantId(), command.deviceId())
                .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
        if (!credential.tenantId().equals(device.tenantId()) || !command.deviceId().equals(device.id())
                || !device.deviceCode().equals(command.deviceCode())) {
            throw new IotException(IotErrorCode.TENANT_VIOLATION, "设备身份不属于当前租户或消息主题");
        }
        if (device.lifecycleStatus() != DeviceLifecycleStatus.Active) {
            throw new IotException(IotErrorCode.DEVICE_INVALID, "设备已停用");
        }
        if (!"MQTT".equalsIgnoreCase(device.protocolType())) {
            throw new IotException(IotErrorCode.PROTOCOL_UNSUPPORTED, "设备协议不是 MQTT");
        }
        DeviceProfile profile = profileRepository.findProfileById(credential.tenantId(), device.deviceProfileId())
                .orElseThrow(() -> new IotException(IotErrorCode.PROFILE_INVALID, "设备模型不存在"));
        if (!credential.tenantId().equals(profile.tenantId()) || !"ACTIVE".equalsIgnoreCase(profile.status())) {
            throw new IotException(IotErrorCode.PROFILE_INVALID, "设备模型已停用或不属于当前租户");
        }
        List<NormalizedMetric> metrics = normalizeMetrics(command.metrics(), profile);
        return new ValidatedMessage(credential.tenantId(), command.deviceId(), key, payloadHash, metrics);
    }

    /**
     * 用途：根据协议优先级生成去重键；入参同时有两个标识时以 message_id 去重，但完整保留两个字段。
     */
    private TelemetryMessageKey messageKey(UUID tenantId, UUID deviceId, String messageId, Long sequence) {
        if (messageId != null && !messageId.isBlank()) {
            String normalized = messageId.trim();
            if (normalized.length() > 128) {
                throw invalid("message_id 长度不能超过 128");
            }
            return new TelemetryMessageKey(tenantId, deviceId, TelemetryMessageKey.KeyType.MESSAGE_ID, normalized,
                    normalized, sequence);
        }
        if (sequence == null || sequence < 0) {
            throw new TelemetryException(TelemetryErrorCode.MISSING_MESSAGE_KEY, null);
        }
        return new TelemetryMessageKey(tenantId, deviceId, TelemetryMessageKey.KeyType.SEQUENCE,
                sequence.toString(), null, sequence);
    }

    /**
     * 用途：校验并规范化 SHA-256 载荷摘要，防止同键比较使用不稳定的外部对象表示。
     */
    private String normalizeHash(String payloadHash) {
        if (payloadHash == null || !payloadHash.matches("[0-9a-fA-F]{64}")) {
            throw invalid("payload_hash 必须是 64 位十六进制摘要");
        }
        return payloadHash.toLowerCase(Locale.ROOT);
    }

    /**
     * 用途：按模型白名单、值类型、单位和必填规则一次性校验所有指标；出参为可持久化规范值。
     */
    private List<NormalizedMetric> normalizeMetrics(List<TelemetryMetric> submitted, DeviceProfile profile) {
        if (submitted == null || submitted.isEmpty()) {
            throw invalid("metrics 不能为空");
        }
        Map<String, DeviceProfile.MetricDefinition> definitions = new HashMap<>();
        for (DeviceProfile.MetricDefinition definition : profile.metrics()) {
            definitions.put(definition.metricCode(), definition);
        }
        Set<String> seen = new HashSet<>();
        List<NormalizedMetric> normalized = new java.util.ArrayList<>();
        for (TelemetryMetric metric : submitted) {
            if (metric == null || metric.metricCode() == null || metric.metricCode().isBlank()) {
                throw invalid("指标编码不能为空");
            }
            String code = metric.metricCode().trim();
            DeviceProfile.MetricDefinition definition = definitions.get(code);
            if (definition == null || !seen.add(code)) {
                throw invalid("指标未被设备模型允许或重复上报: " + code);
            }
            String unit = metric.metricUnit() == null || metric.metricUnit().isBlank()
                    ? definition.unit() : metric.metricUnit().trim();
            if (definition.unit() != null && !definition.unit().equals(unit)) {
                throw invalid("指标单位不匹配: " + code);
            }
            String value = normalizeValue(metric.metricValue(), definition.valueType(), code);
            if ("running_status".equals(code)) {
                value = normalizeRunningStatus(value);
            }
            normalized.add(new NormalizedMetric(code, value, unit));
        }
        for (DeviceProfile.MetricDefinition definition : profile.metrics()) {
            if (definition.required() && !seen.contains(definition.metricCode())) {
                throw invalid("缺少必填指标: " + definition.metricCode());
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * 用途：按 NUMBER/BOOLEAN/TEXT 三类模型值类型规范化指标值；非法值拒绝整条消息。
     */
    private String normalizeValue(Object value, MetricValueType type, String metricCode) {
        if (value == null || type == null) {
            throw invalid("指标值为空或类型未定义: " + metricCode);
        }
        try {
            return switch (type) {
                case NUMBER -> new BigDecimal(value.toString()).toPlainString();
                case BOOLEAN -> normalizeBoolean(value, metricCode);
                case TEXT -> value instanceof String ? (String) value : invalidValue(metricCode);
            };
        } catch (NumberFormatException exception) {
            throw invalid("指标值类型错误: " + metricCode);
        }
    }

    /**
     * 用途：只接受 JSON 布尔或 true/false 文本，避免任意字符串进入布尔指标。
     */
    private String normalizeBoolean(Object value, String metricCode) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue.toString();
        }
        if (value instanceof String stringValue && ("true".equalsIgnoreCase(stringValue.trim())
                || "false".equalsIgnoreCase(stringValue.trim()))) {
            return stringValue.trim().toLowerCase(Locale.ROOT);
        }
        return invalidValue(metricCode);
    }

    private String invalidValue(String metricCode) {
        throw invalid("指标值类型错误: " + metricCode);
    }

    /**
     * 用途：提取标准运行状态指标；缺少该指标时保持已有快照；出参为 Idle/Running/Stopped。
     */
    private String runningStatus(List<NormalizedMetric> metrics, String current) {
        return metrics.stream().filter(metric -> "running_status".equals(metric.metricCode())).findFirst()
                .map(NormalizedMetric::metricValue).orElse(current);
    }

    /**
     * 用途：在遥测追加前校验并规范化标准运行状态，避免非法状态造成部分事实落库。
     */
    private String normalizeRunningStatus(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "idle", "空闲" -> "Idle";
            case "running", "运行" -> "Running";
            case "stopped", "停止" -> "Stopped";
            default -> throw invalid("running_status 仅支持 Idle、Running 或 Stopped");
        };
    }

    private TelemetryException invalid(String detail) {
        return new TelemetryException(TelemetryErrorCode.INVALID_MESSAGE, detail);
    }

    private record ValidatedMessage(UUID tenantId, UUID deviceId, TelemetryMessageKey key, String payloadHash,
                                    List<NormalizedMetric> metrics) {
    }

    private record NormalizedMetric(String metricCode, String metricValue, String metricUnit) {
    }
}
