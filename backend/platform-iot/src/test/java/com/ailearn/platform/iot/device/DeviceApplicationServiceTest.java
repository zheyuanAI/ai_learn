package com.ailearn.platform.iot.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.device.application.DeviceApplicationServiceImpl;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.dto.DeviceCreateRequest;
import com.ailearn.platform.iot.device.dto.DeviceLifecycleRequest;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private DeviceRepository repository;

    @Mock
    private DeviceProfileRepository profileRepository;

    private DeviceApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        service = new DeviceApplicationServiceImpl(repository, profileRepository,
                new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(), new ObjectMapper().findAndRegisterModules()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void onlyMqttDevicesCanBeCreated() {
        IotException exception = assertThrows(IotException.class,
                () -> service.create(new DeviceCreateRequest("M-001", "机台", PROFILE_ID, "OPC-UA",
                        null, null, null), "device-protocol-invalid"));

        assertEquals("IOT_DEV_002", exception.getBusinessCode());
        verify(repository, never()).insert(any(Device.class));
    }

    @Test
    void lifecycleCanMoveBothWaysWithOptimisticExpectedStatus() {
        Device active = device(DeviceLifecycleStatus.Active);
        Device disabled = device(DeviceLifecycleStatus.Disabled);
        when(repository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(active));
        when(repository.updateLifecycle(any(UUID.class), any(UUID.class), any(DeviceLifecycleStatus.class),
                any(DeviceLifecycleStatus.class), any(UUID.class), any(OffsetDateTime.class))).thenReturn(disabled);

        assertEquals("Disabled", service.changeLifecycle(DEVICE_ID,
                new DeviceLifecycleRequest("Disabled"), "device-disable" ).lifecycleStatus());

        when(repository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(disabled));
        when(repository.updateLifecycle(any(UUID.class), any(UUID.class), any(DeviceLifecycleStatus.class),
                any(DeviceLifecycleStatus.class), any(UUID.class), any(OffsetDateTime.class))).thenReturn(active);
        assertEquals("Active", service.changeLifecycle(DEVICE_ID,
                new DeviceLifecycleRequest("Active"), "device-enable").lifecycleStatus());
    }

    @Test
    void repeatedLifecycleCommandReplaysAfterTheDeviceAlreadyReachedTargetState() {
        Device active = device(DeviceLifecycleStatus.Active);
        Device disabled = device(DeviceLifecycleStatus.Disabled);
        when(repository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(active));
        when(repository.updateLifecycle(any(UUID.class), any(UUID.class), any(DeviceLifecycleStatus.class),
                any(DeviceLifecycleStatus.class), any(UUID.class), any(OffsetDateTime.class))).thenReturn(disabled);

        var first = service.changeLifecycle(DEVICE_ID, new DeviceLifecycleRequest("Disabled"), "device-disable-retry");
        var replay = service.changeLifecycle(DEVICE_ID, new DeviceLifecycleRequest("Disabled"), "device-disable-retry");

        assertEquals(first.id(), replay.id());
        assertEquals("Disabled", replay.lifecycleStatus());
    }

    @Test
    void deviceCreateUsesTenantScopedProfileAndIdempotency() {
        when(profileRepository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(activeProfile()));
        when(repository.existsDeviceByCode(TENANT_ID, "M-001")).thenReturn(false);
        when(repository.insert(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new DeviceCreateRequest(" M-001 ", "机台", PROFILE_ID, " mqtt ", null, null, null);
        var first = service.create(request, "device-create-repeat");
        var replay = service.create(request, "device-create-repeat");

        assertEquals(first.id(), replay.id());
        verify(repository).insert(any(Device.class));
    }

    private DeviceProfile activeProfile() {
        return new DeviceProfile(PROFILE_ID, TENANT_ID, "machine", "机台", "ACTIVE", 60,
                List.of(new DeviceProfile.MetricDefinition("temperature", "温度", MetricValueType.NUMBER, "C", true)),
                USER_ID, NOW, USER_ID, NOW);
    }

    private Device device(DeviceLifecycleStatus status) {
        return new Device(DEVICE_ID, TENANT_ID, "M-001", "机台", PROFILE_ID, "MQTT", status,
                null, null, null, USER_ID, NOW, USER_ID, NOW);
    }
}
