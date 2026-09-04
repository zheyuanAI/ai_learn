package com.ailearn.platform.core.manufacturing.dispatch.port;

import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import java.util.Optional;
import java.util.UUID;

/** 工序执行读取已发布派工安排的内部端口。 */
public interface DispatchReferencePort {
    /** 按租户读取派工安排，跨租户对象表现为不存在。 */
    Optional<DispatchOrder> find(UUID tenantId, UUID dispatchId);
}
