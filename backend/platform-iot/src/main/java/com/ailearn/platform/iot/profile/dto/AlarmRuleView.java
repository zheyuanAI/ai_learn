package com.ailearn.platform.iot.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** 告警规则响应。 */
public record AlarmRuleView(UUID id, @JsonProperty("rule_code") String ruleCode,
                            @JsonProperty("device_profile_id") UUID deviceProfileId,
                            @JsonProperty("device_id") UUID deviceId,
                            @JsonProperty("metric_code") String metricCode,
                            String operator,
                            @JsonProperty("trigger_threshold") BigDecimal triggerThreshold,
                            @JsonProperty("recovery_threshold") BigDecimal recoveryThreshold,
                            @JsonProperty("alarm_level") String alarmLevel,
                            String status) {
}
