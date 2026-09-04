package com.ailearn.platform.iot.profile.application;

import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.iot.profile.dto.AlarmRuleCreateRequest;
import com.ailearn.platform.iot.profile.dto.AlarmRuleView;
import com.ailearn.platform.iot.profile.dto.DeviceProfileCreateRequest;
import com.ailearn.platform.iot.profile.dto.DeviceProfilePageResult;
import com.ailearn.platform.iot.profile.dto.DeviceProfileView;
import com.ailearn.platform.iot.profile.dto.MetricDefinitionRequest;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeviceProfile 应用服务。
 * 所有写入从可信上下文取得租户和用户，指标白名单在入库前完整校验，规则只允许单指标阈值。
 */
@Service
public class DeviceProfileApplicationServiceImpl implements DeviceProfileApplicationService {
    private final DeviceProfileRepository repository;
    private final IotIdempotencyExecutor idempotency;
    private final DeviceRepository deviceRepository;

    /** Spring 生产构造器，注入设备端口以校验设备级告警规则的同租户模型绑定。 */
    @Autowired
    public DeviceProfileApplicationServiceImpl(DeviceProfileRepository repository, IotIdempotencyExecutor idempotency,
                                               DeviceRepository deviceRepository) {
        this.repository = repository;
        this.idempotency = idempotency;
        this.deviceRepository = deviceRepository;
    }

    /** 兼容不需要设备级规则校验的旧单元测试构造入口。 */
    public DeviceProfileApplicationServiceImpl(DeviceProfileRepository repository, IotIdempotencyExecutor idempotency) {
        this(repository, idempotency, null);
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:manage')")
    @Transactional(rollbackFor = Exception.class)
    public DeviceProfileView create(DeviceProfileCreateRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        DeviceProfile normalized = normalize(request);
        UUID tenantId = TenantContextHolder.requireTenantId();
        String hash = digest(normalized);
        return idempotency.execute("iot:profile:create", tenantId, idempotencyKey, hash,
                DeviceProfileView.class, () -> {
                    if (repository.existsProfileByCode(tenantId, normalized.profileCode())) {
                        throw new ConflictException("当前租户内设备模型编码已存在");
                    }
                    UUID userId = UserContextHolder.requireUserId();
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    DeviceProfile profile = new DeviceProfile(UUID.randomUUID(), tenantId, normalized.profileCode(),
                            normalized.profileName(), "ACTIVE", normalized.offlineTimeoutSeconds(), normalized.metrics(),
                            userId, now, userId, now);
                    repository.insert(profile);
                    return toView(profile);
                });
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:view')")
    public DeviceProfilePageResult page(String profileCode, int page, int size) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        String code = normalizeFilter(profileCode);
        List<DeviceProfileView> records = repository.findPage(tenantId, code,
                        (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream().map(this::toView).toList();
        return new DeviceProfilePageResult(records, repository.count(tenantId, code), normalizedPage, normalizedSize);
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:view')")
    public DeviceProfileView detail(UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        DeviceProfile profile = repository.findProfileById(tenantId, id)
                .orElseThrow(() -> new IotException(IotErrorCode.PROFILE_INVALID, "设备模型不存在或不属于当前租户"));
        return toView(profile);
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:manage')")
    @Transactional(rollbackFor = Exception.class)
    public AlarmRuleView createRule(AlarmRuleCreateRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        if (request == null || request.deviceProfileId() == null) {
            throw new ValidationException("设备模型与规则字段不能为空");
        }
        UUID tenantId = TenantContextHolder.requireTenantId();
        NormalizedRule normalized = normalizeRule(request);
        String hash = digest(new RulePayload(request.deviceProfileId(), normalized));
        return idempotency.execute("iot:alarm-rule:create", tenantId, idempotencyKey, hash,
                AlarmRuleView.class, () -> {
                    DeviceProfile profile = repository.findProfileById(tenantId, request.deviceProfileId())
                            .orElseThrow(() -> new IotException(IotErrorCode.PROFILE_INVALID, "设备模型不存在"));
                    if (!tenantId.equals(profile.tenantId())) {
                        throw new IotException(IotErrorCode.TENANT_VIOLATION, "设备模型不属于当前租户");
                    }
                    if (!request.deviceProfileId().equals(profile.id())) {
                        throw new IotException(IotErrorCode.PROFILE_INVALID, "设备模型主键与请求不一致");
                    }
                    if (!"ACTIVE".equalsIgnoreCase(profile.status())) {
                        throw new IotException(IotErrorCode.PROFILE_INVALID, "设备模型已停用");
                    }
                    validateRule(normalized, profile);
                    validateDeviceAssociation(tenantId, normalized.deviceId(), profile);
                    if (repository.existsRuleByCode(tenantId, normalized.ruleCode())) {
                        throw new ConflictException("当前租户内告警规则编码已存在");
                    }
                    UUID userId = UserContextHolder.requireUserId();
                    AlarmRule rule = new AlarmRule(UUID.randomUUID(), tenantId, normalized.ruleCode(),
                            profile.id(), normalized.deviceId(), normalized.metricCode(), normalized.operator(),
                            normalized.triggerThreshold(), normalized.recoveryThreshold(), normalized.alarmLevel(),
                            "ACTIVE", userId, OffsetDateTime.now(ZoneOffset.UTC));
                    repository.insertRule(rule);
                    return toView(rule);
                });
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:view')")
    public List<AlarmRuleView> rules(UUID profileId, int page, int size) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        return repository.findRules(tenantId, profileId, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream().map(this::toView).toList();
    }

    private DeviceProfile normalize(DeviceProfileCreateRequest request) {
        if (request == null || blank(request.profileCode()) || blank(request.profileName())
                || request.metrics() == null || request.metrics().isEmpty()) {
            throw new ValidationException("设备模型编码、名称和指标不能为空");
        }
        List<DeviceProfile.MetricDefinition> metrics = request.metrics().stream().map(this::normalizeMetric).toList();
        if (metrics.size() != new HashSet<>(metrics.stream().map(DeviceProfile.MetricDefinition::metricCode).toList()).size()) {
            throw new ValidationException("设备模型指标编码不能重复");
        }
        int timeout = request.offlineTimeoutSeconds() == null ? 60 : request.offlineTimeoutSeconds();
        if (timeout < 1 || timeout > 86400) {
            throw new ValidationException("offline_timeout_seconds 必须在 1 到 86400 之间");
        }
        return new DeviceProfile(null, null, text(request.profileCode(), 64), text(request.profileName(), 128),
                "ACTIVE", timeout, metrics, null, null, null, null);
    }

    private DeviceProfile.MetricDefinition normalizeMetric(MetricDefinitionRequest request) {
        if (request == null || blank(request.metricCode()) || blank(request.metricName())) {
            throw new ValidationException("指标编码和名称不能为空");
        }
        MetricValueType type = MetricValueType.parse(request.valueType());
        if (type == null) {
            throw new IotException(IotErrorCode.PROFILE_INVALID, "value_type 仅支持 NUMBER、BOOLEAN、TEXT");
        }
        return new DeviceProfile.MetricDefinition(text(request.metricCode(), 64), text(request.metricName(), 128),
                type, blank(request.unit()) ? null : text(request.unit(), 32), Boolean.TRUE.equals(request.required()));
    }

    private NormalizedRule normalizeRule(AlarmRuleCreateRequest request) {
        if (blank(request.ruleCode()) || blank(request.metricCode()) || blank(request.operator())
                || blank(request.alarmLevel()) || request.triggerThreshold() == null || request.recoveryThreshold() == null) {
            throw new ValidationException("告警规则必填字段不能为空");
        }
        return new NormalizedRule(text(request.ruleCode(), 64), request.deviceId(), text(request.metricCode(), 64),
                request.operator().trim().toUpperCase(java.util.Locale.ROOT), request.triggerThreshold(),
                request.recoveryThreshold(), text(request.alarmLevel(), 16));
    }

    private void validateRule(NormalizedRule request, DeviceProfile profile) {
        if (!profile.metrics().stream().anyMatch(m -> m.metricCode().equals(request.metricCode())
                && m.valueType() == MetricValueType.NUMBER)) {
            throw new IotException(IotErrorCode.ALARM_RULE_INVALID, "告警规则指标必须是 NUMBER 类型且属于白名单");
        }
        if (!List.of("GT", "GTE", "LT", "LTE", "EQ").contains(request.operator())) {
            throw new IotException(IotErrorCode.ALARM_RULE_INVALID, "operator 不受支持");
        }
        if (request.triggerThreshold().scale() > 6 || request.recoveryThreshold().scale() > 6) {
            throw new ValidationException("阈值小数位不能超过 6 位");
        }
        int thresholdOrder = request.triggerThreshold().compareTo(request.recoveryThreshold());
        boolean descendingAlarm = List.of("GT", "GTE").contains(request.operator());
        boolean ascendingAlarm = List.of("LT", "LTE").contains(request.operator());
        if ((descendingAlarm && thresholdOrder <= 0) || (ascendingAlarm && thresholdOrder >= 0)
                || ("EQ".equals(request.operator()) && thresholdOrder != 0)) {
            throw new IotException(IotErrorCode.ALARM_RULE_INVALID,
                    "恢复阈值必须沿告警方向回到安全区间");
        }
    }

    /**
     * 校验设备级规则的设备属于当前租户且绑定同一 DeviceProfile。
     * 入参：可信租户、可选设备 ID 和已加载模型；出参：无；流程：按租户读取设备并比较模型归属。
     */
    private void validateDeviceAssociation(UUID tenantId, UUID deviceId, DeviceProfile profile) {
        if (deviceId == null) {
            return;
        }
        if (deviceRepository == null) {
            throw new IotException(IotErrorCode.DEVICE_INVALID, "设备级规则缺少设备校验端口");
        }
        Device device = deviceRepository.findDeviceById(tenantId, deviceId)
                .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
        if (!tenantId.equals(device.tenantId())) {
            throw new IotException(IotErrorCode.TENANT_VIOLATION, "设备不属于当前租户");
        }
        if (!profile.id().equals(device.deviceProfileId())) {
            throw new IotException(IotErrorCode.PROFILE_INVALID, "设备未绑定当前设备模型");
        }
    }

    private DeviceProfileView toView(DeviceProfile profile) {
        return new DeviceProfileView(profile.id(), profile.profileCode(), profile.profileName(), profile.status(),
                profile.offlineTimeoutSeconds(), profile.metrics(), profile.createdAt(),
                List.of(new DeviceProfileView.AllowedAction("view", true, null),
                        new DeviceProfileView.AllowedAction("manage", "ACTIVE".equals(profile.status()), null)));
    }

    private AlarmRuleView toView(AlarmRule rule) {
        return new AlarmRuleView(rule.id(), rule.ruleCode(), rule.deviceProfileId(), rule.deviceId(), rule.metricCode(),
                rule.operator(), rule.triggerThreshold(), rule.recoveryThreshold(), rule.alarmLevel(), rule.status());
    }

    private static void requireKey(String key) {
        if (blank(key) || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须为 1 到 128 个字符");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalizeFilter(String value) {
        return blank(value) ? null : value.trim();
    }

    private static String text(String value, int max) {
        String result = value.trim();
        if (result.length() > max) {
            throw new ValidationException("字段长度超过限制");
        }
        return result;
    }

    private record NormalizedRule(String ruleCode, UUID deviceId, String metricCode, String operator,
                                  BigDecimal triggerThreshold, BigDecimal recoveryThreshold, String alarmLevel) {
    }

    /** 告警规则幂等摘要载荷；必须把规则所属模型纳入摘要，避免跨模型错误重放。 */
    private record RulePayload(UUID deviceProfileId, NormalizedRule rule) {
    }

    private static String digest(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(new ObjectMapper().valueToTree(value).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成服务端载荷摘要", exception);
        }
    }
}
