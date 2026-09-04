package com.ailearn.platform.iot.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 设备模型指标定义请求。 */
public record MetricDefinitionRequest(
        @JsonProperty("metric_code") String metricCode,
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("value_type") String valueType,
        String unit,
        Boolean required) {
}
