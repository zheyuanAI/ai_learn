package com.ailearn.platform.iot.telemetry.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 演示/测试环境 MQTT 模拟请求；不接受 tenantId、deviceId 或客户端 payloadHash，均由服务端补齐或计算。
 */
public record TelemetrySimulationRequest(
        @JsonProperty("device_code") String deviceCode,
        @JsonProperty("ts") OffsetDateTime timestamp,
        @JsonProperty("message_id") String messageId,
        Long sequence,
        List<TelemetryMetric> metrics) {

    /** 冻结指标集合，避免控制器调用期间请求对象被外部修改。 */
    public TelemetrySimulationRequest {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
