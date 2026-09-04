package com.ailearn.platform.iot.mqtt;

import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionService;
import org.springframework.stereotype.Component;

/**
 * MQTT 到统一遥测摄取服务的最小委托适配器。
 * 本类不连接 Broker、不实现 ACL，也不包含 Task 19 的告警生命周期逻辑。
 */
@Component
public class DelegatingMqttTelemetryConsumer implements MqttTelemetryConsumer {

    private final TelemetryIngestionService ingestionService;

    /**
     * 用途：注入共用摄取服务；入参为统一应用端口。
     */
    public DelegatingMqttTelemetryConsumer(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * 用途：将 MQTT 命令原样交给共用服务；出参为统一摄取结果。
     */
    @Override
    public TelemetryIngestionResult consume(TelemetryIngestionCommand command) {
        return ingestionService.ingest(command);
    }
}
