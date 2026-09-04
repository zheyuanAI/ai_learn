package com.ailearn.platform.iot.contextlink.application;

import com.ailearn.platform.iot.contextlink.domain.ContextLinkResult;
import java.util.UUID;

/** 告警到生产上下文的只读查询补链应用边界。 */
public interface AlarmContextLinkApplicationService {
    /** 入队并立即尝试一次指定告警的自动补链。 */
    ContextLinkResult link(UUID tenantId, UUID alarmId);

    /**
     * 由有权限用户人工补充或更正告警上下文；至少提供一个业务标识，并由幂等键保护。
     */
    ContextLinkResult linkManually(UUID tenantId, UUID alarmId, UUID operationExecutionId,
                                   UUID workOrderId, String idempotencyKey);

    /** 处理当前租户到期任务，返回实际尝试的任务数。 */
    int retryDue(UUID tenantId, int limit);
}
