package com.ailearn.platform.iot.alarm.domain;

/** IoT 告警生命周期状态。 */
public enum AlarmStatus {
    Triggered,
    Acked,
    RecoveredUnacked,
    Recovered
}
