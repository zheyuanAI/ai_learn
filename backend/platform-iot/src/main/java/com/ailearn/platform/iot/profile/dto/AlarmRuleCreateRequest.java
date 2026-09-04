package com.ailearn.platform.iot.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** 创建设备单指标阈值告警规则请求。 */
public record AlarmRuleCreateRequest(
        @JsonProperty("rule_code") String ruleCode,
        @JsonProperty("device_profile_id") UUID deviceProfileId,
        @JsonProperty("device_id") UUID deviceId,
        @JsonProperty("metric_code") String metricCode,
        String operator,
        @JsonProperty("trigger_threshold") BigDecimal triggerThreshold,
        @JsonProperty("recovery_threshold") BigDecimal recoveryThreshold,
        @JsonProperty("alarm_level") String alarmLevel) {
}
