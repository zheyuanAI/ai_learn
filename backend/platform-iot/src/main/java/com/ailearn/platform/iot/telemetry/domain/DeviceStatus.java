package com.ailearn.platform.iot.telemetry.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 设备状态当前快照。
 * sourceTimestamp 仅用于比较延迟消息，不对外替代遥测事实；alarmStatus 保留给后续告警端口维护。
 */
public record DeviceStatus(UUID tenantId, UUID deviceId, String onlineStatus, String runningStatus,
                           String alarmStatus, OffsetDateTime lastSeenAt, String lastMessageKey,
                           OffsetDateTime sourceTimestamp) {

    /**
     * 用途：创建尚无有效遥测的初始状态。
     */
    public static DeviceStatus initial(UUID tenantId, UUID deviceId) {
        return new DeviceStatus(tenantId, deviceId, "Offline", "Idle", "Normal", null, null, null);
    }
}
