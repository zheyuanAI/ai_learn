package com.ailearn.platform.iot.credential;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.credential.application.DeviceCredentialApplicationServiceImpl;
import com.ailearn.platform.iot.credential.domain.CredentialStatus;
import com.ailearn.platform.iot.credential.domain.DeviceCredential;
import com.ailearn.platform.iot.credential.domain.port.CredentialRepository;
import com.ailearn.platform.iot.credential.dto.CredentialCreatedView;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private CredentialRepository repository;

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceCredentialApplicationServiceImpl service;
    private IdempotencyStorage idempotencyStorage;
    private final AtomicReference<DeviceCredential> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        idempotencyStorage = new InMemoryIdempotencyStorage();
        service = new DeviceCredentialApplicationServiceImpl(repository, deviceRepository,
                new IotIdempotencyExecutor(idempotencyStorage, new ObjectMapper().findAndRegisterModules()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void generatedSecretIsReturnedOnlyOnFirstCreateResponseAndStoredAsPbkdf2Material() {
        stubActiveDeviceAndInsert();
        CredentialCreatedView first = service.create(DEVICE_ID, new com.ailearn.platform.iot.credential.dto.CredentialCreateRequest(),
                "credential-create-once");
        CredentialCreatedView replay = service.create(DEVICE_ID, new com.ailearn.platform.iot.credential.dto.CredentialCreateRequest(),
                "credential-create-once");

        assertNotNull(first.plainSecret());
        assertNull(replay.plainSecret());
        DeviceCredential credential = stored.get();
        assertNotNull(credential);
        assertNotEquals(first.plainSecret(), credential.secretHash());
        assertNotEquals(first.plainSecret(), credential.secretSalt());
        assertTrue(credential.secretHash().length() >= 40);
        assertTrue(credential.secretSalt().length() >= 16);
        String cachedResponse = idempotencyStorage.getRecord("iot:credential:create", "credential-create-once", TENANT_ID)
                .map(IdempotentRecord::getResponseBody).orElseThrow();
        assertTrue(!cachedResponse.contains(first.plainSecret()), "幂等缓存不得保留一次性明文");
        verify(repository, times(1)).insert(any(DeviceCredential.class));
    }

    @Test
    void validCredentialVerifiesAndWrongDeviceOrTenantIsRejected() {
        stubActiveDeviceAndInsert();
        CredentialCreatedView created = service.create(DEVICE_ID, new com.ailearn.platform.iot.credential.dto.CredentialCreateRequest(),
                "credential-verify");
        DeviceCredential credential = stored.get();
        when(deviceRepository.findByCode(TENANT_ID, "M-001")).thenReturn(Optional.of(activeDevice(TENANT_ID)));
        when(repository.findByReference(TENANT_ID, credential.credentialReference())).thenReturn(Optional.of(credential));

        assertTrue(service.verify(TENANT_ID, "M-001", credential.credentialReference(), created.plainSecret())
                .id().equals(DEVICE_ID));
        assertThrows(IotException.class,
                () -> service.verify(OTHER_TENANT_ID, "M-001", credential.credentialReference(), created.plainSecret()));
        assertThrows(IotException.class,
                () -> service.verify(TENANT_ID, "OTHER-DEVICE", credential.credentialReference(), created.plainSecret()));
    }

    @Test
    void revocationImmediatelyMakesCredentialInvalid() {
        stubActiveDeviceAndInsert();
        CredentialCreatedView created = service.create(DEVICE_ID, new com.ailearn.platform.iot.credential.dto.CredentialCreateRequest(),
                "credential-revoke");
        DeviceCredential active = stored.get();
        DeviceCredential revoked = new DeviceCredential(active.id(), active.tenantId(), active.deviceId(),
                active.credentialReference(), active.secretHash(), active.secretSalt(), CredentialStatus.Revoked,
                active.createdBy(), active.createdAt(), USER_ID, NOW);
        when(repository.findById(TENANT_ID, DEVICE_ID, active.id())).thenReturn(Optional.of(active), Optional.of(revoked));
        when(repository.revoke(eq(TENANT_ID), eq(DEVICE_ID), eq(active.id()), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(repository.findByReference(TENANT_ID, active.credentialReference())).thenReturn(Optional.of(revoked));
        when(deviceRepository.findByCode(TENANT_ID, "M-001")).thenReturn(Optional.of(activeDevice(TENANT_ID)));

        service.revoke(DEVICE_ID, active.id(), "credential-revoke-command");

        assertThrows(IotException.class,
                () -> service.verify(TENANT_ID, "M-001", active.credentialReference(), created.plainSecret()));
        verify(repository).revoke(eq(TENANT_ID), eq(DEVICE_ID), eq(active.id()), eq(USER_ID), any(OffsetDateTime.class));
    }

    @Test
    void mqttTopicCredentialReferenceResolvesOnlyOneActiveDevice() {
        Device device = activeDevice(TENANT_ID);
        DeviceCredential credential = new DeviceCredential(UUID.randomUUID(), TENANT_ID, DEVICE_ID,
                "cred-topic", "hash", "salt", CredentialStatus.Active, USER_ID, NOW, null, null);
        when(repository.findByReferenceAcrossTenants("cred-topic")).thenReturn(java.util.List.of(credential));
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(device));

        assertTrue(service.verifyReference("cred-topic").equals(device));
    }

    @Test
    void ambiguousOrRevokedMqttTopicCredentialIsRejected() {
        DeviceCredential credential = new DeviceCredential(UUID.randomUUID(), TENANT_ID, DEVICE_ID,
                "cred-topic", "hash", "salt", CredentialStatus.Revoked, USER_ID, NOW, USER_ID, NOW);
        when(repository.findByReferenceAcrossTenants("cred-topic")).thenReturn(java.util.List.of(credential));
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(activeDevice(TENANT_ID)));

        assertThrows(IotException.class, () -> service.verifyReference("cred-topic"));

        when(repository.findByReferenceAcrossTenants("ambiguous"))
                .thenReturn(java.util.List.of(credential, credential));
        assertThrows(IotException.class, () -> service.verifyReference("ambiguous"));
    }

    @Test
    void credentialManagementDoesNotFallBackToAResourceFromAnotherTenant() {
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.empty());

        IotException exception = assertThrows(IotException.class,
                () -> service.create(DEVICE_ID, new com.ailearn.platform.iot.credential.dto.CredentialCreateRequest(),
                        "credential-cross-tenant"));

        assertTrue(exception.getMessage().contains("IOT_DEV_001"));
        verify(repository, never()).insert(any(DeviceCredential.class));
    }

    private Device activeDevice(UUID tenantId) {
        return new Device(DEVICE_ID, tenantId, "M-001", "机台", UUID.randomUUID(), "MQTT",
                DeviceLifecycleStatus.Active, null, null, null, USER_ID, NOW, USER_ID, NOW);
    }

    private void stubActiveDeviceAndInsert() {
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(activeDevice(TENANT_ID)));
        when(repository.insert(any(DeviceCredential.class))).thenAnswer(invocation -> {
            DeviceCredential credential = invocation.getArgument(0);
            stored.set(credential);
            return credential;
        });
    }
}
