package com.ailearn.platform.iot.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.application.DeviceProfileApplicationServiceImpl;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.iot.profile.dto.AlarmRuleCreateRequest;
import com.ailearn.platform.iot.profile.dto.DeviceProfileCreateRequest;
import com.ailearn.platform.iot.profile.dto.DeviceProfileView;
import com.ailearn.platform.iot.profile.dto.MetricDefinitionRequest;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
class DeviceProfileApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private DeviceProfileRepository repository;

    @Mock
    private DeviceRepository deviceRepository;

    private DeviceProfileApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        service = new DeviceProfileApplicationServiceImpl(repository,
                new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(), new ObjectMapper().findAndRegisterModules()),
                deviceRepository);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void createRejectsDuplicateMetricCodesAfterNormalization() {
        DeviceProfileCreateRequest request = new DeviceProfileCreateRequest(
                "machine", "机台", List.of(
                new MetricDefinitionRequest("temperature", "温度", "NUMBER", "C", true),
                new MetricDefinitionRequest(" temperature ", "温度2", "NUMBER", "C", false)), 60);

        assertThrows(com.ailearn.platform.shared.exception.ValidationException.class,
                () -> service.create(request, "profile-create-duplicate"));
        verify(repository, never()).insert(any(DeviceProfile.class));
    }

    @Test
    void sameCreateKeyReplaysTheFirstNormalizedResult() {
        when(repository.existsProfileByCode(TENANT_ID, "machine")).thenReturn(false);
        when(repository.insert(any(DeviceProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceProfileCreateRequest request = new DeviceProfileCreateRequest(
                " machine ", "机台", List.of(new MetricDefinitionRequest("temperature", "温度", "NUMBER", "C", true)), 60);
        DeviceProfileView first = service.create(request, "profile-create-idempotent");
        DeviceProfileView replay = service.create(request, "profile-create-idempotent");

        assertEquals(first.id(), replay.id());
        assertEquals(first.metrics(), replay.metrics());
        verify(repository, times(1)).insert(any(DeviceProfile.class));
    }

    @Test
    void deviceScopedRuleMustUseTheSameProfileAndTenant() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID)));
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.of(
                device(DEVICE_ID, OTHER_PROFILE_ID, TENANT_ID)));

        IotException mismatch = assertThrows(IotException.class,
                () -> service.createRule(ruleRequest(DEVICE_ID), "rule-profile-mismatch"));
        assertEquals("IOT_PROFILE_001", mismatch.getBusinessCode());
        verify(repository, never()).insertRule(any(AlarmRule.class));
    }

    @Test
    void deviceScopedRuleHidesCrossTenantDevice() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID)));
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID)).thenReturn(Optional.empty());

        IotException missing = assertThrows(IotException.class,
                () -> service.createRule(ruleRequest(DEVICE_ID), "rule-cross-tenant"));
        assertEquals("IOT_DEV_001", missing.getBusinessCode());
        verify(repository, never()).insertRule(any(AlarmRule.class));
    }

    @Test
    void ruleRejectsRepositoryReturningAProfileDifferentFromRequestedId() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(OTHER_PROFILE_ID)));

        IotException mismatch = assertThrows(IotException.class,
                () -> service.createRule(ruleRequest(null), "rule-profile-id-mismatch"));

        assertEquals("IOT_PROFILE_001", mismatch.getBusinessCode());
        verify(repository, never()).insertRule(any(AlarmRule.class));
    }

    @Test
    void profileScopedRuleValidatesMetricWhitelistWithoutDevice() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID)));
        when(repository.existsRuleByCode(TENANT_ID, "temperature-high")).thenReturn(false);
        when(repository.insertRule(any(AlarmRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createRule(ruleRequest(null), "rule-profile-scoped");

        assertEquals("temperature", result.metricCode());
        verify(repository).insertRule(any(AlarmRule.class));
    }

    @Test
    void ruleIdempotencyDigestIncludesProfileId() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID)));
        when(repository.existsRuleByCode(TENANT_ID, "temperature-high")).thenReturn(false);
        when(repository.insertRule(any(AlarmRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createRule(ruleRequest(null), "rule-profile-scoped-key");
        AlarmRuleCreateRequest otherProfileRequest = new AlarmRuleCreateRequest("temperature-high", OTHER_PROFILE_ID,
                null, "temperature", "GT", new BigDecimal("80"), new BigDecimal("70"), "HIGH");

        IotException conflict = assertThrows(IotException.class,
                () -> service.createRule(otherProfileRequest, "rule-profile-scoped-key"));

        assertEquals("IOT_IDEMP_001", conflict.getBusinessCode());
        verify(repository, times(1)).insertRule(any(AlarmRule.class));
    }

    @Test
    void ruleRejectsRecoveryThresholdOnTheWrongSideOfTrigger() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID)));

        AlarmRuleCreateRequest request = new AlarmRuleCreateRequest("temperature-high", PROFILE_ID, null,
                "temperature", "GT", new BigDecimal("80"), new BigDecimal("90"), "HIGH");

        IotException exception = assertThrows(IotException.class, () -> service.createRule(request, "rule-direction"));

        assertEquals("IOT_ALM_002", exception.getBusinessCode());
        verify(repository, never()).insertRule(any(AlarmRule.class));
    }

    @Test
    void equalityRuleRequiresTheSameRecoveryThreshold() {
        when(repository.findProfileById(TENANT_ID, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID)));

        AlarmRuleCreateRequest request = new AlarmRuleCreateRequest("temperature-eq", PROFILE_ID, null,
                "temperature", "EQ", new BigDecimal("80"), new BigDecimal("81"), "HIGH");

        IotException exception = assertThrows(IotException.class, () -> service.createRule(request, "rule-eq-direction"));

        assertEquals("IOT_ALM_002", exception.getBusinessCode());
        verify(repository, never()).insertRule(any(AlarmRule.class));
    }

    private DeviceProfile profile(UUID id) {
        return new DeviceProfile(id, TENANT_ID, "machine", "机台", "ACTIVE", 60,
                List.of(new DeviceProfile.MetricDefinition("temperature", "温度", MetricValueType.NUMBER, "C", true)),
                USER_ID, NOW, USER_ID, NOW);
    }

    private Device device(UUID id, UUID profileId, UUID tenantId) {
        return new Device(id, tenantId, "M-001", "机台", profileId, "MQTT", DeviceLifecycleStatus.Active,
                null, null, null, USER_ID, NOW, USER_ID, NOW);
    }

    private AlarmRuleCreateRequest ruleRequest(UUID deviceId) {
        return new AlarmRuleCreateRequest("temperature-high", PROFILE_ID, deviceId, " temperature ", "gt",
                new BigDecimal("80"), new BigDecimal("70"), "HIGH");
    }
}
