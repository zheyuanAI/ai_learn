package com.ailearn.platform.iot.contextlink.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 告警补链所需的最小本地事实；不携带遥测载荷。 */
public record AlarmContextCandidate(UUID id, UUID tenantId, UUID deviceId,
                                    OffsetDateTime alarmTime, String contextSource,
                                    String contextStatus, UUID operationExecutionId,
                                    UUID workOrderId) {
}
