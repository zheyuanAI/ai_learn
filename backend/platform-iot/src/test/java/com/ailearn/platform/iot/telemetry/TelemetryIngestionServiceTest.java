package com.ailearn.platform.iot.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.iot.telemetry.application.TelemetryCredentialContext;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionService;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionServiceImpl;
import com.ailearn.platform.iot.telemetry.application.TelemetryMetric;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryMessageKey;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.ailearn.platform.iot.telemetry.infrastructure.InMemoryTelemetryStore;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceProfileRepository profileRepository;

    private InMemoryTelemetryStore store;
    private TelemetryIngestionService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryTelemetryStore();
        service = new TelemetryIngestionServiceImpl(deviceRepository, profileRepository, store, store, store,
                (command, status) -> { });
        lenient().when(deviceRepository.findDeviceById(any(UUID.class), eq(DEVICE_ID)))
                .thenAnswer(invocation -> Optional.of(device(invocation.getArgument(0))));
        lenient().when(profileRepository.findProfileById(any(UUID.class), eq(PROFILE_ID)))
                .thenAnswer(invocation -> Optional.of(profile(invocation.getArgument(0))));
    }

    @Test
    void messageIdTakesPriorityAndDeduplicationKeyContainsDeviceId() {
        TelemetryIngestionResult result = service.ingest(command(TENANT_ID, "msg-1", 9L,
                BASE_TIME, List.of(metric("temperature", new BigDecimal("42.5")))));

        assertEquals(DEVICE_ID + "|message_id|msg-1", result.messageKey());
        assertEquals(1, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    @Test
    void messageIdPriorityStillRetainsSequenceNumberOnTheDomainKey() {
        TelemetryMessageKey key = new TelemetryMessageKey(TENANT_ID, DEVICE_ID,
                TelemetryMessageKey.KeyType.MESSAGE_ID, "msg-with-sequence", "msg-with-sequence", 42L);

        assertEquals("msg-with-sequence", key.messageId());
        assertEquals(42L, key.sequenceNo());
    }

    @Test
    void sequenceIsUsedWhenMessageIdIsAbsent() {
        TelemetryIngestionResult result = service.ingest(command(TENANT_ID, null, 9L,
                BASE_TIME, List.of(metric("temperature", new BigDecimal("42.5")))));

        assertEquals(DEVICE_ID + "|sequence|9", result.messageKey());
    }

    @Test
    void sameKeyAndHashIsIdempotentWithoutSecondFactOrStatusNotification() {
        TelemetryIngestionCommand command = command(TENANT_ID, "msg-1", null, BASE_TIME,
                List.of(metric("temperature", new BigDecimal("42.5"))));

        TelemetryIngestionResult first = service.ingest(command);
        TelemetryIngestionResult duplicate = service.ingest(command);

        assertEquals(false, first.duplicate());
        assertEquals(true, duplicate.duplicate());
        assertEquals(first.telemetryIds(), duplicate.telemetryIds());
        assertEquals(1, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    @Test
    void sameKeyWithDifferentHashIsRejectedAsConflict() {
        TelemetryIngestionCommand first = command(TENANT_ID, "msg-1", null, BASE_TIME,
                List.of(metric("temperature", new BigDecimal("42.5"))));
        TelemetryIngestionCommand conflict = command(TENANT_ID, "msg-1", null, BASE_TIME,
                List.of(metric("temperature", new BigDecimal("43.5"))));

        service.ingest(first);
        TelemetryException exception = assertThrows(TelemetryException.class, () -> service.ingest(conflict));

        assertEquals("IOT_TLM_003", exception.getBusinessCode());
        assertEquals(1, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    @Test
    void missingMessageIdentityIsRejectedBeforeAnyPersistence() {
        TelemetryException exception = assertThrows(TelemetryException.class,
                () -> service.ingest(command(TENANT_ID, null, null, BASE_TIME,
                        List.of(metric("temperature", new BigDecimal("42.5"))))));

        assertEquals("IOT_TLM_002", exception.getBusinessCode());
        assertEquals(0, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    @Test
    void invalidMetricRejectsTheWholeMessage() {
        TelemetryException exception = assertThrows(TelemetryException.class,
                () -> service.ingest(command(TENANT_ID, "msg-invalid", null, BASE_TIME,
                        List.of(metric("temperature", new BigDecimal("42.5")), metric("unknown", true)))));

        assertEquals("IOT_TLM_001", exception.getBusinessCode());
        assertEquals(0, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    @Test
    void invalidRunningStatusIsRejectedBeforeAnyMetricIsStored() {
        TelemetryException exception = assertThrows(TelemetryException.class,
                () -> service.ingest(command(TENANT_ID, "msg-running-invalid", null, BASE_TIME,
                        List.of(metric("temperature", new BigDecimal("42.5")), metric("running_status", "Broken")))));

        assertEquals("IOT_TLM_001", exception.getBusinessCode());
        assertEquals(0, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    @Test
    void delayedMessageIsStoredButCannotMoveTheCurrentStatusBackwards() {
        service.ingest(command(TENANT_ID, "newer", null, BASE_TIME.plusMinutes(2),
                List.of(metric("temperature", new BigDecimal("42.5")), metric("running_status", "Running"))));

        TelemetryIngestionResult delayed = service.ingest(command(TENANT_ID, "older", null, BASE_TIME,
                List.of(metric("temperature", new BigDecimal("40.0")), metric("running_status", "Idle"))));

        DeviceStatus status = delayed.status();
        assertEquals(4, store.telemetryCount(TENANT_ID, DEVICE_ID));
        assertEquals("Running", status.runningStatus());
        assertEquals(BASE_TIME.plusMinutes(2), status.sourceTimestamp());
        assertEquals(DEVICE_ID + "|message_id|newer", status.lastMessageKey());
    }

    @Test
    void sameMessageIdentityCanBeUsedByDifferentTenantsWithoutCrossTenantDeduplication() {
        service.ingest(command(TENANT_ID, "same", null, BASE_TIME,
                List.of(metric("temperature", new BigDecimal("42.5")))));
        TelemetryIngestionResult secondTenant = service.ingest(command(OTHER_TENANT_ID, "same", null, BASE_TIME,
                List.of(metric("temperature", new BigDecimal("42.5")))));

        assertEquals(false, secondTenant.duplicate());
        assertEquals(1, store.telemetryCount(TENANT_ID, DEVICE_ID));
        assertEquals(1, store.telemetryCount(OTHER_TENANT_ID, DEVICE_ID));
    }

    @Test
    void disabledDeviceIsRejectedBeforePersistence() {
        when(deviceRepository.findDeviceById(TENANT_ID, DEVICE_ID))
                .thenReturn(Optional.of(new Device(DEVICE_ID, TENANT_ID, "M-001", "机台", PROFILE_ID,
                        "MQTT", DeviceLifecycleStatus.Disabled, null, null, null, null, BASE_TIME, null, BASE_TIME)));

        IotException exception = assertThrows(IotException.class,
                () -> service.ingest(command(TENANT_ID, "disabled", null, BASE_TIME,
                        List.of(metric("temperature", new BigDecimal("42.5"))))));

        assertEquals("IOT_DEV_001", exception.getBusinessCode());
        assertEquals(0, store.telemetryCount(TENANT_ID, DEVICE_ID));
    }

    private TelemetryIngestionCommand command(UUID tenantId, String messageId, Long sequence,
                                              OffsetDateTime timestamp, List<TelemetryMetric> metrics) {
        return new TelemetryIngestionCommand(new TelemetryCredentialContext(tenantId, DEVICE_ID, "cred-1"),
                DEVICE_ID, "M-001", timestamp, timestamp.plusSeconds(1), messageId, sequence, metrics,
                hash(messageId + ":" + sequence + ":" + metrics));
    }

    private TelemetryMetric metric(String code, Object value) {
        return new TelemetryMetric(code, value, "temperature".equals(code) ? "C" : null);
    }

    private Device device(UUID tenantId) {
        return new Device(DEVICE_ID, tenantId, "M-001", "机台", PROFILE_ID, "MQTT", DeviceLifecycleStatus.Active,
                null, null, null, null, BASE_TIME, null, BASE_TIME);
    }

    private DeviceProfile profile(UUID tenantId) {
        return new DeviceProfile(PROFILE_ID, tenantId, "machine", "机台", "ACTIVE", 60,
                List.of(
                        new DeviceProfile.MetricDefinition("temperature", "温度", MetricValueType.NUMBER, "C", true),
                        new DeviceProfile.MetricDefinition("running_status", "运行状态", MetricValueType.TEXT, null, false)),
                null, BASE_TIME, null, BASE_TIME);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
