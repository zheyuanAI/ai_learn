package com.ailearn.platform.iot.alarm.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * IoT 告警事实；只在告警生命周期发生变化时更新，不依赖 Core 的可用性。
 */
public record AlarmFact(UUID id, UUID tenantId, String alarmNo, UUID deviceId, UUID ruleId,
                        String alarmType, String alarmLevel, AlarmStatus status,
                        OffsetDateTime triggeredAt, OffsetDateTime ackedAt, UUID ackUserId,
                        OffsetDateTime recoveredAt, UUID operationExecutionId, UUID workOrderId,
                        String contextSource, String contextStatus, OffsetDateTime createdAt,
                        String ackComment, OffsetDateTime updatedAt, UUID updatedBy) {

    /**
     * 兼容已有告警事实构造调用；历史调用未提供确认备注时按 null 保存。
     *
     * @param id 告警标识
     * @param tenantId 租户标识
     * @param alarmNo 告警编号
     * @param deviceId 设备标识
     * @param ruleId 告警规则标识
     * @param alarmType 告警类型
     * @param alarmLevel 告警级别
     * @param status 告警状态
     * @param triggeredAt 触发时间
     * @param ackedAt 确认时间
     * @param ackUserId 确认人
     * @param recoveredAt 恢复时间
     * @param operationExecutionId 关联工序执行标识
     * @param workOrderId 关联工单标识
     * @param contextSource 上下文来源
     * @param contextStatus 上下文状态
     * @param createdAt 创建时间
     */
    public AlarmFact(UUID id, UUID tenantId, String alarmNo, UUID deviceId, UUID ruleId,
                     String alarmType, String alarmLevel, AlarmStatus status,
                     OffsetDateTime triggeredAt, OffsetDateTime ackedAt, UUID ackUserId,
                     OffsetDateTime recoveredAt, UUID operationExecutionId, UUID workOrderId,
                     String contextSource, String contextStatus, OffsetDateTime createdAt) {
        this(id, tenantId, alarmNo, deviceId, ruleId, alarmType, alarmLevel, status, triggeredAt,
                ackedAt, ackUserId, recoveredAt, operationExecutionId, workOrderId, contextSource,
                contextStatus, createdAt, null, createdAt, null);
    }

    /** 兼容已有告警事实构造；新增审计字段未由旧调用方提供时从创建时间初始化。 */
    public AlarmFact(UUID id, UUID tenantId, String alarmNo, UUID deviceId, UUID ruleId,
                     String alarmType, String alarmLevel, AlarmStatus status,
                     OffsetDateTime triggeredAt, OffsetDateTime ackedAt, UUID ackUserId,
                     OffsetDateTime recoveredAt, UUID operationExecutionId, UUID workOrderId,
                     String contextSource, String contextStatus, OffsetDateTime createdAt,
                     String ackComment) {
        this(id, tenantId, alarmNo, deviceId, ruleId, alarmType, alarmLevel, status, triggeredAt,
                ackedAt, ackUserId, recoveredAt, operationExecutionId, workOrderId, contextSource,
                contextStatus, createdAt, ackComment, createdAt, null);
    }
}
