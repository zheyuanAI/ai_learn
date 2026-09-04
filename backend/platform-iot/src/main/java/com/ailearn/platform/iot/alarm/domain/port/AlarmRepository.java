package com.ailearn.platform.iot.alarm.domain.port;

import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 告警事实持久化端口；每个方法都显式接收可信租户。 */
public interface AlarmRepository {

    /**
     * 用途：按设备+规则原子创建活动告警；出参为新建或竞争中已存在的活动告警。
     */
    AlarmFact createIfAbsent(AlarmFact fact);

    Optional<AlarmFact> findById(UUID tenantId, UUID alarmId);

    Optional<AlarmFact> findActive(UUID tenantId, UUID deviceId, UUID ruleId);

    /** 查询当前租户设备是否仍存在未恢复告警，用于维护 DeviceStatus.alarm_status。 */
    boolean hasActiveForDevice(UUID tenantId, UUID deviceId);

    /**
     * 用途：按期望状态 CAS 推进生命周期；出参为空表示租户、告警或期望状态不匹配。
     */
    Optional<AlarmFact> transition(UUID tenantId, UUID alarmId, AlarmStatus expected,
                                   AlarmStatus target, OffsetDateTime at, UUID userId);

    /**
     * 推进告警状态并保存确认备注；未涉及确认的状态迁移默认复用旧端口语义。
     *
     * @param tenantId 可信租户标识
     * @param alarmId 告警标识
     * @param expected 期望当前状态
     * @param target 目标状态
     * @param at 状态迁移时间
     * @param userId 操作人，恢复自动迁移时为空
     * @param ackComment 确认备注，非确认迁移时为空
     * @return CAS 成功后的告警事实
     */
    default Optional<AlarmFact> transition(UUID tenantId, UUID alarmId, AlarmStatus expected,
                                            AlarmStatus target, OffsetDateTime at, UUID userId,
                                            String ackComment) {
        return transition(tenantId, alarmId, expected, target, at, userId);
    }

    List<AlarmFact> findPage(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
                             OffsetDateTime from, OffsetDateTime to, String contextStatus,
                             int offset, int limit);

    long count(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
               OffsetDateTime from, OffsetDateTime to, String contextStatus);
}
