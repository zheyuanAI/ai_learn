package com.ailearn.platform.iot.device.application;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.dto.DeviceCreateRequest;
import com.ailearn.platform.iot.device.dto.DeviceLifecycleRequest;
import com.ailearn.platform.iot.device.dto.DevicePageResult;
import com.ailearn.platform.iot.device.dto.DeviceView;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备应用服务。
 * 入参：设备管理请求与原始幂等键；出参：只含设备身份和后端计算动作的视图；流程：可信上下文、模型校验、状态校验、事务持久化。
 */
@Service
public class DeviceApplicationServiceImpl implements DeviceApplicationService {
    private final DeviceRepository repository;
    private final DeviceProfileRepository profileRepository;
    private final IotIdempotencyExecutor idempotency;

    public DeviceApplicationServiceImpl(DeviceRepository repository, DeviceProfileRepository profileRepository,
                                        IotIdempotencyExecutor idempotency) {
        this.repository = repository;
        this.profileRepository = profileRepository;
        this.idempotency = idempotency;
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:manage')")
    @Transactional(rollbackFor = Exception.class)
    public DeviceView create(DeviceCreateRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        if (request == null || blank(request.deviceCode()) || blank(request.deviceName())
                || request.deviceProfileId() == null) {
            throw new ValidationException("设备编码、名称和设备模型不能为空");
        }
        if (blank(request.protocolType())) {
            throw new IotException(IotErrorCode.PROTOCOL_UNSUPPORTED, "protocol_type 只允许 MQTT");
        }
        String protocol = text(request.protocolType(), 16).toUpperCase(java.util.Locale.ROOT);
        if (!"MQTT".equals(protocol)) {
            throw new IotException(IotErrorCode.PROTOCOL_UNSUPPORTED, "protocol_type 只允许 MQTT");
        }
        UUID tenantId = TenantContextHolder.requireTenantId();
        String code = text(request.deviceCode(), 64);
        String name = text(request.deviceName(), 128);
        String hash = digest(new DeviceCreatePayload(code, name, request.deviceProfileId(), protocol,
                request.workCenterId(), request.areaId(), request.mapPointId()));
        return idempotency.execute("iot:device:create", tenantId, idempotencyKey, hash,
                DeviceView.class, () -> {
                    DeviceProfile profile = profileRepository.findProfileById(tenantId, request.deviceProfileId())
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
                    if (repository.existsDeviceByCode(tenantId, code)) {
                        throw new ConflictException("当前租户内设备编码已存在");
                    }
                    UUID userId = UserContextHolder.requireUserId();
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    Device device = new Device(UUID.randomUUID(), tenantId, code, name, profile.id(), protocol,
                            DeviceLifecycleStatus.Active, request.workCenterId(), request.areaId(), request.mapPointId(),
                            userId, now, userId, now);
                    repository.insert(device);
                    return toView(device);
                });
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:view')")
    public DevicePageResult page(String deviceCode, String lifecycleStatus, int page, int size) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        DeviceLifecycleStatus status = lifecycleStatus == null || lifecycleStatus.isBlank()
                ? null : DeviceLifecycleStatus.parse(lifecycleStatus);
        if (lifecycleStatus != null && !lifecycleStatus.isBlank() && status == null) {
            throw new ValidationException("lifecycle_status 仅支持 Active 或 Disabled");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 200);
        String code = blank(deviceCode) ? null : deviceCode.trim();
        List<DeviceView> records = repository.findPage(tenantId, code, status,
                        (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream().map(this::toView).toList();
        return new DevicePageResult(records, repository.count(tenantId, code, status), normalizedPage, normalizedSize);
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:view')")
    public DeviceView detail(UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return repository.findDeviceById(tenantId, id).map(this::toView)
                .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:manage')")
    @Transactional(rollbackFor = Exception.class)
    public DeviceView changeLifecycle(UUID id, DeviceLifecycleRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        DeviceLifecycleStatus target = request == null ? null : DeviceLifecycleStatus.parse(request.lifecycleStatus());
        if (target == null) {
            throw new ValidationException("lifecycle_status 仅支持 Active 或 Disabled");
        }
        UUID tenantId = TenantContextHolder.requireTenantId();
        String hash = digest(new LifecyclePayload(id, target));
        return idempotency.execute("iot:device:lifecycle", tenantId, idempotencyKey, hash,
                DeviceView.class, () -> {
                    Device existing = repository.findDeviceById(tenantId, id)
                            .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
                    if (existing.lifecycleStatus() == target) {
                        throw new ConflictException("设备已处于目标生命周期状态");
                    }
                    UUID userId = UserContextHolder.requireUserId();
                    Device updated = repository.updateLifecycle(tenantId, id, existing.lifecycleStatus(), target,
                            userId, OffsetDateTime.now(ZoneOffset.UTC));
                    if (updated == null) {
                        throw new ConflictException("设备生命周期已被其他请求修改");
                    }
                    return toView(updated);
                });
    }

    private DeviceView toView(Device device) {
        boolean active = device.lifecycleStatus() == DeviceLifecycleStatus.Active;
        return DeviceView.from(device, List.of(
                new DeviceView.AllowedAction("view", true, null),
                new DeviceView.AllowedAction("enable", !active, active ? "设备已启用" : null),
                new DeviceView.AllowedAction("disable", active, active ? null : "设备已停用")));
    }

    private static void requireKey(String key) {
        if (blank(key) || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须为 1 到 128 个字符");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String text(String value, int max) {
        String result = value.trim();
        if (result.length() > max) {
            throw new ValidationException("字段长度超过限制");
        }
        return result;
    }

    private static String digest(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(value).toString()
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成服务端载荷摘要", exception);
        }
    }

    private record LifecyclePayload(UUID id, DeviceLifecycleStatus target) {
    }

    private record DeviceCreatePayload(String deviceCode, String deviceName, UUID deviceProfileId, String protocolType,
                                       UUID workCenterId, UUID areaId, UUID mapPointId) {
    }
}
