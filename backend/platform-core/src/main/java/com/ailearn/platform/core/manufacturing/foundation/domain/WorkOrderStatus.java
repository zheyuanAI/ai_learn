package com.ailearn.platform.core.manufacturing.foundation.domain;

/** foundation 阶段只创建 Draft 工单，后续 S5 执行层扩展完整状态机。 */
public enum WorkOrderStatus {
    Draft,
    PendingApproval,
    Released,
    InProgress,
    Completed,
    Rejected
}
