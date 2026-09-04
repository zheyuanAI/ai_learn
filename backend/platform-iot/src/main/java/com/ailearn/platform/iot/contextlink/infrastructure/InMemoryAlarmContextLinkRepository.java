package com.ailearn.platform.iot.contextlink.infrastructure;

import com.ailearn.platform.iot.contextlink.domain.AlarmContextCandidate;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkTask;
import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.ailearn.platform.iot.contextlink.domain.port.AlarmContextLinkRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 测试与本地演示用补链仓储；生产环境由 PostgreSQL 适配器承接。 */
public class InMemoryAlarmContextLinkRepository implements AlarmContextLinkRepository {
    private final Map<UUID, AlarmContextCandidate> alarms = new LinkedHashMap<>();
    private final Map<UUID, ContextLinkTask> tasks = new LinkedHashMap<>();

    public synchronized void putAlarm(AlarmContextCandidate alarm) {
        alarms.put(alarm.id(), alarm);
    }

    public synchronized Optional<AlarmContextCandidate> alarm(UUID alarmId) {
        return Optional.ofNullable(alarms.get(alarmId));
    }

    public synchronized Optional<ContextLinkTask> task(UUID alarmId) {
        return tasks.values().stream().filter(task -> task.alarmId().equals(alarmId)).findFirst();
    }

    @Override
    public synchronized Optional<AlarmContextCandidate> findAlarm(UUID tenantId, UUID alarmId) {
        return Optional.ofNullable(alarms.get(alarmId)).filter(value -> tenantId.equals(value.tenantId()));
    }

    @Override
    public synchronized void enqueue(UUID tenantId, UUID alarmId, OffsetDateTime nextRetryAt) {
        Optional<ContextLinkTask> existing = task(alarmId);
        if (existing.isEmpty()) {
            UUID id = UUID.randomUUID();
            tasks.put(id, new ContextLinkTask(id, tenantId, alarmId, "Pending", 0, nextRetryAt));
        } else if (!"Completed".equals(existing.get().status())) {
            ContextLinkTask value = existing.get();
            tasks.put(value.id(), new ContextLinkTask(value.id(), value.tenantId(), value.alarmId(),
                    "Pending", value.retryCount(), nextRetryAt));
        }
    }

    @Override
    public synchronized Optional<ContextLinkTask> claimDue(UUID tenantId, UUID alarmId, OffsetDateTime now) {
        return tasks.values().stream()
                .filter(value -> tenantId.equals(value.tenantId()) && alarmId.equals(value.alarmId()))
                .filter(value -> ("Pending".equals(value.status()) || "Retry".equals(value.status()))
                        && (value.nextRetryAt() == null || !value.nextRetryAt().isAfter(now)))
                .findFirst().map(this::claim);
    }

    @Override
    public synchronized Optional<ContextLinkTask> claimNextDue(UUID tenantId, OffsetDateTime now) {
        return tasks.values().stream()
                .filter(value -> tenantId.equals(value.tenantId()))
                .filter(value -> ("Pending".equals(value.status()) || "Retry".equals(value.status()))
                        && (value.nextRetryAt() == null || !value.nextRetryAt().isAfter(now)))
                .findFirst().map(this::claim);
    }

    private ContextLinkTask claim(ContextLinkTask task) {
        ContextLinkTask claimed = new ContextLinkTask(task.id(), task.tenantId(), task.alarmId(),
                "Processing", task.retryCount(), task.nextRetryAt());
        tasks.put(task.id(), claimed);
        return claimed;
    }

    @Override
    public synchronized boolean linkAutomatically(UUID tenantId, UUID alarmId,
                                                   ProductionContextView context, OffsetDateTime linkedAt) {
        AlarmContextCandidate current = alarms.get(alarmId);
        if (current == null || !tenantId.equals(current.tenantId())
                || !"Pending".equals(current.contextStatus())
                || current.operationExecutionId() != null || current.workOrderId() != null) {
            return false;
        }
        alarms.put(alarmId, new AlarmContextCandidate(current.id(), current.tenantId(), current.deviceId(),
                current.alarmTime(), "Automatic", "Linked", context.operationExecutionId(), context.workOrderId()));
        return true;
    }

    @Override
    public synchronized boolean linkManually(UUID tenantId, UUID alarmId, UUID operationExecutionId,
                                              UUID workOrderId, OffsetDateTime linkedAt) {
        AlarmContextCandidate current = alarms.get(alarmId);
        if (current == null || !tenantId.equals(current.tenantId()) || "Linked".equals(current.contextStatus())) {
            return false;
        }
        alarms.put(alarmId, new AlarmContextCandidate(current.id(), current.tenantId(), current.deviceId(),
                current.alarmTime(), "Manual", "Linked",
                operationExecutionId == null ? current.operationExecutionId() : operationExecutionId,
                workOrderId == null ? current.workOrderId() : workOrderId));
        return true;
    }

    @Override
    public synchronized void markCompleted(UUID tenantId, UUID taskId, OffsetDateTime completedAt) {
        ContextLinkTask current = tasks.get(taskId);
        if (current != null && tenantId.equals(current.tenantId())) {
            tasks.put(taskId, new ContextLinkTask(current.id(), current.tenantId(), current.alarmId(),
                    "Completed", current.retryCount(), null));
        }
    }

    @Override
    public synchronized void markRetry(UUID tenantId, UUID taskId, int retryCount,
                                        OffsetDateTime nextRetryAt, String error, OffsetDateTime updatedAt) {
        ContextLinkTask current = tasks.get(taskId);
        if (current != null && tenantId.equals(current.tenantId())) {
            tasks.put(taskId, new ContextLinkTask(current.id(), current.tenantId(), current.alarmId(),
                    "Retry", retryCount, nextRetryAt));
        }
    }
}
