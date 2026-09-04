package com.ailearn.platform.iot.mqtt;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionService;
import java.util.List;
import org.junit.jupiter.api.Test;

class MqttTelemetryConsumerTest {

    @Test
    void delegatesMqttMessageToTheCommonTelemetryService() {
        TelemetryIngestionService service = mock(TelemetryIngestionService.class);
        MqttTelemetryConsumer consumer = new DelegatingMqttTelemetryConsumer(service);
        TelemetryIngestionCommand command = mock(TelemetryIngestionCommand.class);
        TelemetryIngestionResult expected = new TelemetryIngestionResult(true, false, "key", List.of(), null);
        when(service.ingest(command)).thenReturn(expected);

        assertSame(expected, consumer.consume(command));
        verify(service).ingest(command);
    }
}
