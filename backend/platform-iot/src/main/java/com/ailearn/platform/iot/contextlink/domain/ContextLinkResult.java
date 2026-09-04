package com.ailearn.platform.iot.contextlink.domain;

import java.util.UUID;

/** 告警上下文补链结果；Pending/Retry 状态不返回伪造的生产标识。 */
public record ContextLinkResult(UUID alarmId, Status status, ProductionContextView context,
                                int retryCount, String detail) {
    public enum Status {
        LINKED,
        ALREADY_LINKED,
        RETRY_SCHEDULED,
        NOT_FOUND,
        NOT_DUE
    }
}
