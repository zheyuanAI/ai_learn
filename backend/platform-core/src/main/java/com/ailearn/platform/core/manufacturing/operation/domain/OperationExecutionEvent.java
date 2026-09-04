package com.ailearn.platform.core.manufacturing.operation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 工序执行时间线事件；暂停原因只在 PAUSED 事件中保存。 */
public record OperationExecutionEvent(OperationExecutionEventType type, OffsetDateTime occurredAt,
                                      UUID operatorId, String reason) {
    public OperationExecutionEvent {
        if (type == null || occurredAt == null || operatorId == null) {
            throw new IllegalArgumentException("工序执行事件字段不合法");
        }
        if (type == OperationExecutionEventType.PAUSED
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("暂停必须填写原因");
        }
        if (reason != null && reason.length() > 512) {
            throw new IllegalArgumentException("执行事件原因不能超过 512 个字符");
        }
    }
}
