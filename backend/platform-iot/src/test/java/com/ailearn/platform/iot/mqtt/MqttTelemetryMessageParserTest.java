package com.ailearn.platform.iot.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.credential.application.DeviceCredentialVerifier;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MqttTelemetryMessageParserTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime RECEIVED_AT = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private DeviceCredentialVerifier credentialVerifier;

    @Mock
    private MqttTelemetryConsumer consumer;

    private MqttTelemetryMessageParser parser;

    @BeforeEach
    void setUp() {
        RequestContextHolder.clear();
        parser = new MqttTelemetryMessageParser(credentialVerifier, consumer,
                new ObjectMapper().findAndRegisterModules(), Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"),
                        ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void resolvesCredentialFromTopicAndDelegatesTrustedCommand() {
        Device device = device();
        when(credentialVerifier.verifyReference("cred-topic")).thenReturn(device);
        TelemetryIngestionResult expected = new TelemetryIngestionResult(true, false, "key", List.of(), null);
        when(consumer.consume(any())).thenReturn(expected);
        byte[] payload = """
                {"message_id":"msg-1","sequence":7,"ts":"2026-09-04T09:59:00Z",
                 "metrics":[{"metric_code":"temperature","metric_value":42.5,"metric_unit":"C"}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(expected, parser.accept("devices/cred-topic/telemetry", payload));

        ArgumentCaptor<TelemetryIngestionCommand> captor = ArgumentCaptor.forClass(TelemetryIngestionCommand.class);
        verify(consumer).consume(captor.capture());
        TelemetryIngestionCommand command = captor.getValue();
        assertEquals(TENANT_ID, command.credentialContext().tenantId());
        assertEquals(DEVICE_ID, command.credentialContext().deviceId());
        assertEquals("cred-topic", command.credentialContext().credentialReference());
        assertEquals("M-001", command.deviceCode());
        assertEquals(OffsetDateTime.parse("2026-09-04T09:59:00Z"), command.timestamp());
        assertEquals(RECEIVED_AT, command.receivedAt());
        assertEquals("msg-1", command.messageId());
        assertEquals(7L, command.sequence());
        assertEquals(1, command.metrics().size());
        assertEquals(64, command.payloadHash().length());
    }

    @Test
    void rejectsTopicPayloadIdentityMismatchBeforeDelegation() {
        when(credentialVerifier.verifyReference("cred-topic")).thenReturn(device());

        IotException exception = assertThrows(IotException.class, () -> parser.accept(
                "devices/cred-topic/telemetry", """
                        {"ts":"2026-09-04T10:00:00Z","sequence":1,
                         "device_code":"OTHER","metrics":[]}
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("IOT_TENANT_001"));
        verify(consumer, never()).consume(any());
    }

    @Test
    void rejectsNonCredentialTopicAndInvalidJson() {
        assertThrows(RuntimeException.class, () -> parser.accept("devices/M-001/status", new byte[0]));
        when(credentialVerifier.verifyReference("cred-topic")).thenReturn(device());
        assertThrows(RuntimeException.class, () -> parser.accept("devices/cred-topic/telemetry", "not-json"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        verify(consumer, never()).consume(any());
    }

    private Device device() {
        return new Device(DEVICE_ID, TENANT_ID, "M-001", "机台", UUID.randomUUID(), "MQTT",
                DeviceLifecycleStatus.Active, null, null, null, null, RECEIVED_AT, null, RECEIVED_AT);
    }
}
