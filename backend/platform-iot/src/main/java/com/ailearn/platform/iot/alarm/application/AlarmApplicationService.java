package com.ailearn.platform.iot.alarm.application;

import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.dto.AlarmPageResult;
import com.ailearn.platform.iot.alarm.dto.AlarmView;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 告警查询与人工确认应用服务。
 * <p>
 * 告警的触发和恢复由已保存的遥测事实驱动，人工确认只推进告警生命周期，不重新计算或修改遥测事实；写入确认必须带幂等键。
 * </p>
 * <p>
 * 查询和状态变更均在当前租户与权限边界内执行；告警已被其他请求推进、已处于终态或不属于当前租户时，应返回稳定的业务错误而不是伪造成功。
 * </p>
 */
public interface AlarmApplicationService {
    /** 查询当前租户可见的告警分页，非法分页或时间范围由应用层拒绝。 */
    AlarmPageResult page(UUID deviceId, AlarmStatus status, String alarmLevel, OffsetDateTime from,
                         OffsetDateTime to, String contextStatus, int page, int size);

    /** 查询单条告警详情；不存在或跨租户的记录不向调用方暴露。 */
    AlarmView detail(UUID alarmId);

    /**
     * 执行人工确认：Triggered -> Acked，RecoveredUnacked -> Recovered；重复确认或并发状态变化必须保持幂等并拒绝非法迁移。
     */
    AlarmView ack(UUID alarmId, String ackComment, String idempotencyKey);
}
