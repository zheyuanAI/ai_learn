package com.ailearn.platform.iot.alarm.dto;

import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 告警查询响应；仅返回当前可信租户内的告警事实。 */
public record AlarmView(UUID id, @JsonProperty("alarm_no") String alarmNo,
                        @JsonProperty("device_id") UUID deviceId,
                        @JsonProperty("rule_id") UUID ruleId,
                        @JsonProperty("alarm_type") String alarmType,
                        @JsonProperty("alarm_level") String alarmLevel,
                        String status,
                        @JsonProperty("triggered_at") OffsetDateTime triggeredAt,
                        @JsonProperty("acked_at") OffsetDateTime ackedAt,
                        @JsonProperty("ack_user_id") UUID ackUserId,
                        @JsonProperty("recovered_at") OffsetDateTime recoveredAt,
                        @JsonProperty("operation_execution_id") UUID operationExecutionId,
                        @JsonProperty("work_order_id") UUID workOrderId,
                        @JsonProperty("context_source") String contextSource,
                        @JsonProperty("context_status") String contextStatus,
                        @JsonProperty("ack_comment") String ackComment) {

    public static AlarmView from(AlarmFact fact) {
        return new AlarmView(fact.id(), fact.alarmNo(), fact.deviceId(), fact.ruleId(), fact.alarmType(),
                fact.alarmLevel(), fact.status().name(), fact.triggeredAt(), fact.ackedAt(), fact.ackUserId(),
                fact.recoveredAt(), fact.operationExecutionId(), fact.workOrderId(), fact.contextSource(),
                fact.contextStatus(), fact.ackComment());
    }
}
