package com.ailearn.platform.core.manufacturing.operation.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 独立工序执行聚合。设备为可选软引用，时间线和操作人通过不可变事件保存。
 */
public record OperationExecution(UUID id, UUID tenantId, UUID dispatchId, UUID workOrderId,
                                 UUID operationId, UUID deviceId, OperationExecutionStatus status,
                                 List<OperationExecutionEvent> events, long version) {
    public OperationExecution {
        if (id == null || tenantId == null || dispatchId == null || workOrderId == null
                || operationId == null || status == null || events == null || version < 0) {
            throw new IllegalArgumentException("工序执行基础字段不合法");
        }
        events = List.copyOf(events);
        if (status == OperationExecutionStatus.NotStarted && !events.isEmpty()) {
            throw new IllegalArgumentException("未开始工序不能有执行事件");
        }
    }

    /** 创建未开始执行记录。 */
    public static OperationExecution notStarted(UUID id, UUID tenantId, UUID dispatchId,
                                                UUID workOrderId, UUID operationId, UUID deviceId) {
        return new OperationExecution(id, tenantId, dispatchId, workOrderId, operationId,
                deviceId, OperationExecutionStatus.NotStarted, List.of(), 0);
    }

    /** 开始执行并记录开始时间和操作人。 */
    public OperationExecution start(UUID operatorId, OffsetDateTime at) {
        require(OperationExecutionStatus.NotStarted, "只有 NotStarted 工序可以开始");
        return with(OperationExecutionStatus.Running,
                event(OperationExecutionEventType.STARTED, operatorId, at, null));
    }

    /** 暂停执行，暂停原因必填。 */
    public OperationExecution pause(String reason, UUID operatorId, OffsetDateTime at) {
        require(OperationExecutionStatus.Running, "只有 Running 工序可以暂停");
        return with(OperationExecutionStatus.Paused,
                event(OperationExecutionEventType.PAUSED, operatorId, at, reason));
    }

    /** 恢复暂停中的执行。 */
    public OperationExecution resume(UUID operatorId, OffsetDateTime at) {
        require(OperationExecutionStatus.Paused, "只有 Paused 工序可以恢复");
        return with(OperationExecutionStatus.Running,
                event(OperationExecutionEventType.RESUMED, operatorId, at, null));
    }

    /** 完成执行并记录完成时间和操作人。 */
    public OperationExecution complete(UUID operatorId, OffsetDateTime at) {
        require(OperationExecutionStatus.Running, "只有 Running 工序可以完成");
        return with(OperationExecutionStatus.Completed,
                event(OperationExecutionEventType.COMPLETED, operatorId, at, null));
    }

    /** 判断该执行在指定告警时间是否仍为活动上下文。 */
    public boolean activeAt(OffsetDateTime alarmTime) {
        if (alarmTime == null || events.isEmpty()) {
            return false;
        }
        OperationExecutionStatus historical = OperationExecutionStatus.NotStarted;
        for (OperationExecutionEvent event : events) {
            if (event.occurredAt().isAfter(alarmTime)) {
                break;
            }
            historical = switch (event.type()) {
                case STARTED, RESUMED -> OperationExecutionStatus.Running;
                case PAUSED -> OperationExecutionStatus.Paused;
                case COMPLETED -> OperationExecutionStatus.Completed;
            };
        }
        return historical == OperationExecutionStatus.Running
                || historical == OperationExecutionStatus.Paused;
    }

    /** 返回开始时间；未开始时为空。 */
    public OffsetDateTime startedAt() {
        return events.stream().filter(e -> e.type() == OperationExecutionEventType.STARTED)
                .map(OperationExecutionEvent::occurredAt).findFirst().orElse(null);
    }

    /** 返回时间线最后事件时间。 */
    public OffsetDateTime lastEventAt() {
        return events.isEmpty() ? null : events.get(events.size() - 1).occurredAt();
    }

    /** 返回告警时刻之前最后一条执行事件的时间，供上下文摘要定位事实边界。 */
    public OffsetDateTime eventAt(OffsetDateTime at) {
        return events.stream().filter(event -> !event.occurredAt().isAfter(at))
                .map(OperationExecutionEvent::occurredAt).reduce((first, second) -> second).orElse(null);
    }

    private OperationExecution with(OperationExecutionStatus next, OperationExecutionEvent event) {
        if (!events.isEmpty() && event.occurredAt().isBefore(lastEventAt())) {
            throw new IllegalArgumentException("执行事件时间不能早于上一条事件");
        }
        List<OperationExecutionEvent> timeline = new ArrayList<>(events);
        timeline.add(event);
        return new OperationExecution(id, tenantId, dispatchId, workOrderId, operationId,
                deviceId, next, timeline, version + 1);
    }

    private OperationExecutionEvent event(OperationExecutionEventType type, UUID operatorId,
                                          OffsetDateTime at, String reason) {
        return new OperationExecutionEvent(type, at, operatorId, reason);
    }

    private void require(OperationExecutionStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }
}
