package com.ailearn.platform.iot.telemetry.domain.port;

import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 设备状态快照端口。
 * 状态更新必须按 sourceTimestamp 单调推进，延迟消息只能保存遥测而不能覆盖较新的快照。
 */
public interface DeviceStatusPort {

    /**
     * 用途：读取当前租户下设备状态；不存在时返回初始 Offline 状态。
     */
    DeviceStatus find(UUID tenantId, UUID deviceId);

    /**
     * 用途：仅在候选消息时间较新时更新状态；出参表示是否推进以及最终状态。
     */
    StatusUpdateResult updateIfNewer(DeviceStatus candidate);

    /**
     * 按设备模型的 offline_timeout_seconds 批量把超时在线状态降为 Offline；生产实现必须以数据库条件更新保证并发安全。
     * 默认空实现用于旧 focused 测试端口，避免改变其构造和行为。
     */
    default int markOfflineIfTimedOut(OffsetDateTime now) {
        return 0;
    }

    /**
     * 更新当前租户设备的告警快照；实现必须只更新状态表，不修改遥测时间线。
     * 默认空实现用于不需要状态联动的 focused 测试适配器。
     *
     * @param tenantId 可信租户
     * @param deviceId 设备标识
     * @param alarmStatus Normal 或 Alarm
     */
    default void updateAlarmStatus(UUID tenantId, UUID deviceId, String alarmStatus) {
        // 非生产测试端口无需维护告警快照。
    }

    /** 状态推进结果。 */
    record StatusUpdateResult(boolean updated, DeviceStatus status) {
    }
}
