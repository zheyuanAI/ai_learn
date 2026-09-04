package com.ailearn.platform.iot.credential.application;

import com.ailearn.platform.iot.credential.domain.CredentialStatus;
import com.ailearn.platform.iot.credential.domain.DeviceCredential;
import com.ailearn.platform.iot.credential.domain.port.CredentialRepository;
import com.ailearn.platform.iot.credential.dto.CredentialCreateRequest;
import com.ailearn.platform.iot.credential.dto.CredentialCreatedView;
import com.ailearn.platform.iot.credential.dto.CredentialView;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备凭证应用服务。
 * 入参：当前租户设备、无 secret 创建请求及幂等键；出参：创建时一次性明文或非敏感凭证视图。
 * 流程：校验设备生命周期、随机生成 secret、仅持久化 PBKDF2 摘要，撤销使用条件更新立即失效。
 */
@Service
public class DeviceCredentialApplicationServiceImpl implements DeviceCredentialApplicationService, DeviceCredentialVerifier {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SECRET_BYTES = 32;
    private final CredentialRepository repository;
    private final DeviceRepository deviceRepository;
    private final IotIdempotencyExecutor idempotency;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceCredentialApplicationServiceImpl(CredentialRepository repository, DeviceRepository deviceRepository,
                                                  IotIdempotencyExecutor idempotency) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.idempotency = idempotency;
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:manage')")
    @Transactional(rollbackFor = Exception.class)
    public CredentialCreatedView create(UUID deviceId, CredentialCreateRequest request, String idempotencyKey) {
        requireKey(idempotencyKey);
        CredentialCreateRequest normalizedRequest = request == null ? new CredentialCreateRequest() : request;
        UUID tenantId = TenantContextHolder.requireTenantId();
        String hash = digest(new CreatePayload(deviceId, normalizedRequest));
        return idempotency.execute("iot:credential:create", tenantId, idempotencyKey, hash,
                CredentialCreatedView.class, () -> {
                    Device device = requireDevice(tenantId, deviceId);
                    if (device.lifecycleStatus() != DeviceLifecycleStatus.Active) {
                        throw new IotException(IotErrorCode.DEVICE_INVALID, "已停用设备不能创建接入凭证");
                    }
                    UUID userId = UserContextHolder.requireUserId();
                    byte[] secretBytes = new byte[SECRET_BYTES];
                    secureRandom.nextBytes(secretBytes);
                    String plainSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
                    byte[] saltBytes = new byte[16];
                    secureRandom.nextBytes(saltBytes);
                    String salt = Base64.getEncoder().encodeToString(saltBytes);
                    DeviceCredential credential = new DeviceCredential(UUID.randomUUID(), tenantId, device.id(),
                            "cred_" + UUID.randomUUID().toString().replace("-", ""),
                            hashSecret(plainSecret, saltBytes), salt, CredentialStatus.Active, userId,
                            OffsetDateTime.now(ZoneOffset.UTC), null, null);
                    repository.insert(credential);
                    return new CredentialCreatedView(credential.id(), device.id(), credential.credentialReference(),
                            credential.status().name(), plainSecret, credential.createdAt());
                }, result -> new CredentialCreatedView(result.id(), result.deviceId(), result.credentialReference(),
                        result.credentialStatus(), null, result.createdAt()));
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:view')")
    public List<CredentialView> list(UUID deviceId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        requireDevice(tenantId, deviceId);
        return repository.findByDevice(tenantId, deviceId).stream().map(this::toView).toList();
    }

    @Override
    @PreAuthorize("hasAuthority('iot:device:manage')")
    @Transactional(rollbackFor = Exception.class)
    public CredentialView revoke(UUID deviceId, UUID credentialId, String idempotencyKey) {
        requireKey(idempotencyKey);
        UUID tenantId = TenantContextHolder.requireTenantId();
        String hash = digest(new RevokePayload(deviceId, credentialId));
        return idempotency.execute("iot:credential:revoke", tenantId, idempotencyKey, hash,
                CredentialView.class, () -> {
                    requireDevice(tenantId, deviceId);
                    DeviceCredential current = repository.findById(tenantId, deviceId, credentialId)
                            .orElseThrow(() -> new IotException(IotErrorCode.CREDENTIAL_INVALID, "凭证不存在或不属于设备"));
                    if (current.status() != CredentialStatus.Active) {
                        throw new IotException(IotErrorCode.CREDENTIAL_INVALID, "凭证已撤销或不可用");
                    }
                    UUID userId = UserContextHolder.requireUserId();
                    if (!repository.revoke(tenantId, deviceId, credentialId, userId, OffsetDateTime.now(ZoneOffset.UTC))) {
                        throw new ConflictException("凭证已被其他请求撤销");
                    }
                    DeviceCredential revoked = repository.findById(tenantId, deviceId, credentialId)
                            .orElseThrow(() -> new IotException(IotErrorCode.CREDENTIAL_INVALID, "凭证不存在"));
                    return toView(revoked);
                });
    }

    @Override
    public Device verify(UUID tenantId, String deviceCode, String credentialReference, String plainSecret) {
        if (tenantId == null || blank(deviceCode) || blank(credentialReference) || blank(plainSecret)) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, null);
        }
        Device device = deviceRepository.findByCode(tenantId, deviceCode.trim())
                .orElseThrow(() -> new IotException(IotErrorCode.CREDENTIAL_INVALID, null));
        DeviceCredential credential = repository.findByReference(tenantId, credentialReference.trim())
                .orElseThrow(() -> new IotException(IotErrorCode.CREDENTIAL_INVALID, null));
        if (!tenantId.equals(device.tenantId()) || !tenantId.equals(credential.tenantId())
                || !credential.deviceId().equals(device.id()) || credential.status() != CredentialStatus.Active
                || device.lifecycleStatus() != DeviceLifecycleStatus.Active
                || !verifySecret(plainSecret, credential.secretHash(), credential.secretSalt())) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, null);
        }
        return device;
    }

    /**
     * 用途：验证 MQTT 主题中的凭证引用并解析设备身份；入参不含租户，租户从唯一凭证记录恢复。
     * 出参：已确认租户、设备、生命周期和协议的 Device；流程：拒绝空值/歧义凭证，再校验设备和凭证同租户。
     */
    @Override
    public Device verifyReference(String credentialReference) {
        if (blank(credentialReference)) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, null);
        }
        List<DeviceCredential> matches = repository.findByReferenceAcrossTenants(credentialReference.trim());
        if (matches.size() != 1) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, null);
        }
        DeviceCredential credential = matches.get(0);
        Device device = deviceRepository.findDeviceById(credential.tenantId(), credential.deviceId())
                .orElseThrow(() -> new IotException(IotErrorCode.CREDENTIAL_INVALID, null));
        if (!credential.tenantId().equals(device.tenantId())
                || !credential.deviceId().equals(device.id())
                || credential.status() != CredentialStatus.Active
                || device.lifecycleStatus() != DeviceLifecycleStatus.Active
                || !"MQTT".equalsIgnoreCase(device.protocolType())) {
            throw new IotException(IotErrorCode.CREDENTIAL_INVALID, null);
        }
        return device;
    }

    private Device requireDevice(UUID tenantId, UUID deviceId) {
        return deviceRepository.findDeviceById(tenantId, deviceId)
                .filter(device -> tenantId.equals(device.tenantId()))
                .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
    }

    private CredentialView toView(DeviceCredential credential) {
        return new CredentialView(credential.id(), credential.deviceId(), credential.credentialReference(),
                credential.status().name(), credential.createdAt(), credential.revokedAt());
    }

    private String hashSecret(String plainSecret, byte[] salt) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(plainSecret.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded());
        } catch (Exception exception) {
            throw new IllegalStateException("设备凭证摘要算法不可用", exception);
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
        }
    }

    private boolean verifySecret(String plainSecret, String expected, String salt) {
        try {
            byte[] actual = Base64.getDecoder().decode(hashSecret(plainSecret, Base64.getDecoder().decode(salt)));
            return MessageDigest.isEqual(actual, Base64.getDecoder().decode(expected));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void requireKey(String key) {
        if (blank(key) || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须为 1 到 128 个字符");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
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

    private record CreatePayload(UUID deviceId, CredentialCreateRequest request) {
    }

    private record RevokePayload(UUID deviceId, UUID credentialId) {
    }
}
