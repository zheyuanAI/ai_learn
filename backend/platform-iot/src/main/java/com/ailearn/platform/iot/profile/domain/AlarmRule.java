package com.ailearn.platform.iot.profile.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 单指标阈值告警规则；复杂组合算法不进入一期。 */
public record AlarmRule(UUID id, UUID tenantId, String ruleCode, UUID deviceProfileId, UUID deviceId,
                        String metricCode, String operator, BigDecimal triggerThreshold,
                        BigDecimal recoveryThreshold, String alarmLevel, String status,
                        UUID createdBy, OffsetDateTime createdAt) {
}
