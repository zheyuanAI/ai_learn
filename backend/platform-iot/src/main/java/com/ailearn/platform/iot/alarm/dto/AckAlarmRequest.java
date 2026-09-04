package com.ailearn.platform.iot.alarm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 告警确认请求；确认备注只用于审计语义，不会改写触发/恢复时间。 */
public record AckAlarmRequest(@JsonProperty("ack_comment") String ackComment) {
}
