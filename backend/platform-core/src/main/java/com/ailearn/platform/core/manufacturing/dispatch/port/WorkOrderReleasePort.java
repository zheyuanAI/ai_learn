package com.ailearn.platform.core.manufacturing.dispatch.port;

import java.util.UUID;

/** 工单状态内部查询端口；派工/工序包不直接改写工单执行聚合。 */
public interface WorkOrderReleasePort {
    /** 判断指定租户工单当前是否已下达（Released）。 */
    boolean isReleased(UUID tenantId, UUID workOrderId);
}
