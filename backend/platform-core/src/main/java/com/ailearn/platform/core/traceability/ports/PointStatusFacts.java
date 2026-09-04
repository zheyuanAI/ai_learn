package com.ailearn.platform.core.traceability.ports;

import java.time.Instant;
import java.util.UUID;

/** IoT 为设备点位提供的状态/告警展示事实。 */
public record PointStatusFacts(UUID alarmId, boolean alarm, boolean offline, boolean warning,
                               String alarmLevel, String alarmStatus, Instant occurredAt,
                               Instant sourceUpdatedAt) {
    /** 兼容旧测试和旧适配器；新适配器应提供当前活动告警标识。 */
    public PointStatusFacts(boolean alarm, boolean offline, boolean warning, String alarmLevel,
                            String alarmStatus, Instant occurredAt, Instant sourceUpdatedAt) {
        this(null, alarm, offline, warning, alarmLevel, alarmStatus, occurredAt, sourceUpdatedAt);
    }
}
