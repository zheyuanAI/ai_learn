package com.ailearn.platform.iot.contextlink.domain.port;

import com.ailearn.platform.iot.contextlink.domain.AlarmContextCandidate;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkTask;
import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * IoT 告警上下文补链持久化边界；实现只能更新 IoT 自有告警字段和既有补链任务表。
 */
public interface AlarmContextLinkRepository {
    Optional<AlarmContextCandidate> findAlarm(UUID tenantId, UUID alarmId);

    /** 确保告警有一个待处理任务；重复调用必须保持幂等。 */
    void enqueue(UUID tenantId, UUID alarmId, OffsetDateTime nextRetryAt);

    /** 在当前事务内锁定并领取指定告警的到期任务。 */
    Optional<ContextLinkTask> claimDue(UUID tenantId, UUID alarmId, OffsetDateTime now);

    /** 在当前事务内锁定并领取当前租户任一到期任务。 */
    Optional<ContextLinkTask> claimNextDue(UUID tenantId, OffsetDateTime now);

    /** 仅在告警仍为 Pending 且未被人工补链时写入自动上下文。 */
    boolean linkAutomatically(UUID tenantId, UUID alarmId, ProductionContextView context,
                              OffsetDateTime linkedAt);

    /** 人工更新 IoT 告警上下文；不修改告警时间线或原始遥测事实。 */
    boolean linkManually(UUID tenantId, UUID alarmId, UUID operationExecutionId,
                         UUID workOrderId, OffsetDateTime linkedAt);

    void markCompleted(UUID tenantId, UUID taskId, OffsetDateTime completedAt);

    /** 失败只记录任务重试信息，不改写告警的生产上下文标识。 */
    void markRetry(UUID tenantId, UUID taskId, int retryCount,
                   OffsetDateTime nextRetryAt, String error, OffsetDateTime updatedAt);
}
