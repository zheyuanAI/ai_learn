package com.ailearn.platform.iot.telemetry.application;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单个遥测指标命令值。
 * metricValue 保留为对象以兼容 JSON 的数字、布尔和文本三类值，服务边界会按 DeviceProfile 重新校验并规范化。
 */
public record TelemetryMetric(@JsonProperty("metric_code") String metricCode,
                              @JsonProperty("metric_value") Object metricValue,
                              @JsonProperty("metric_unit") String metricUnit) {
}
