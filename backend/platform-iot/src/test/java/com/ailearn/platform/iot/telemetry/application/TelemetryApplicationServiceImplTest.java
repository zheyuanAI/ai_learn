package com.ailearn.platform.iot.telemetry.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryQueryPort;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelemetryApplicationServiceImplTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void simulationIsDisabledWhenProductionFlagIsFalse() {
        TelemetryApplicationServiceImpl service = service(false);

        IotException exception = assertThrows(IotException.class,
                () -> service.simulate(new TelemetrySimulationRequest("M-001",
                        OffsetDateTime.parse("2026-09-04T10:00:00Z"), "message-1", null, List.of())));

        org.junit.jupiter.api.Assertions.assertEquals("IOT_TLM_004", exception.getBusinessCode());
    }

    @Test
    void legacyTestConstructorCanStillEnableSimulationExplicitly() {
        TelemetryApplicationServiceImpl service = service(true);
        // 进入统一服务前先准备设备，随后由模拟入口复用真实摄取校验。
        when(deviceRepository.findByCode(TENANT_ID, "M-001")).thenReturn(Optional.of(device()));

        when(ingestionService.ingest(Mockito.any())).thenReturn(
                new TelemetryIngestionResult(true, false, "key", List.of(), DeviceStatus.initial(TENANT_ID, DEVICE_ID)));

        service.simulate(new TelemetrySimulationRequest("M-001",
                OffsetDateTime.parse("2026-09-04T10:00:00Z"), "message-1", null, List.of()));
    }

    private DeviceRepository deviceRepository;
    private TelemetryIngestionService ingestionService;

    private TelemetryApplicationServiceImpl service(boolean enabled) {
        deviceRepository = Mockito.mock(DeviceRepository.class);
        TelemetryQueryPort queryPort = Mockito.mock(TelemetryQueryPort.class);
        DeviceStatusPort statusPort = Mockito.mock(DeviceStatusPort.class);
        ingestionService = Mockito.mock(TelemetryIngestionService.class);
        return new TelemetryApplicationServiceImpl(deviceRepository, queryPort, statusPort, ingestionService,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-09-04T10:01:00Z"), ZoneOffset.UTC), enabled);
    }

    private Device device() {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-04T10:00:00Z");
        return new Device(DEVICE_ID, TENANT_ID, "M-001", "机台", PROFILE_ID, "MQTT",
                DeviceLifecycleStatus.Active, null, null, null, null, now, null, now);
    }
}
