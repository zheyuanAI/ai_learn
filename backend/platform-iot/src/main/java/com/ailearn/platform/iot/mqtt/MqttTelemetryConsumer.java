package com.ailearn.platform.iot.mqtt;

import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;

/**
 * MQTT 遥测消费入口端口。
 * 解析、认证后的消息必须委托给共享 TelemetryIngestionService，避免 MQTT 和模拟入口各自实现业务规则。
 */
public interface MqttTelemetryConsumer {

    /**
     * 用途：消费一条已解析的 MQTT 遥测命令；出参与模拟入口一致。
     */
    TelemetryIngestionResult consume(TelemetryIngestionCommand command);
}
