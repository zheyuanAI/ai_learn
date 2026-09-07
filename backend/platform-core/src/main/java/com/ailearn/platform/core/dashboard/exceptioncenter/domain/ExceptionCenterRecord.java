package com.ailearn.platform.core.dashboard.exceptioncenter.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/** 异常中心由实时 Facts 指标派生的只读异常记录，不落第二份事实表。 */
public record ExceptionCenterRecord(String source,
                                    @JsonProperty("exception_type") String exceptionType,
                                    String severity,
                                    BigDecimal value,
                                    String message,
                                    Instant occurredAt) {
}
