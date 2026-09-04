package com.ailearn.platform.core.manufacturing.execution.application;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import java.util.Optional;
import java.util.UUID;

/** WorkOrder 完整生命周期应用端口；不暴露库存写入能力。 */
public interface WorkOrderExecutionService {

    /** 创建 Draft 工单并登记其必需工序集合。 */
    WorkOrderLifecycle createWorkOrder(WorkOrderCreateRequest request, String idempotencyKey);

    /** 提交 Draft 或 Rejected 工单进入 PendingApproval。 */
    WorkOrderLifecycle submit(UUID workOrderId, String idempotencyKey);

    /** 审核 PendingApproval 工单并锁定 BOM/Routing 版本。 */
    WorkOrderLifecycle approve(UUID workOrderId, String idempotencyKey);

    /** 驳回 PendingApproval 工单；reason 为必填拒绝原因。 */
    WorkOrderLifecycle reject(UUID workOrderId, String reason, String idempotencyKey);

    /** 在首次有效工序执行开始时推进工单到 InProgress。 */
    WorkOrderLifecycle startProduction(UUID workOrderId, String idempotencyKey);

    /** 接收后续执行链路的进度快照，不伪造任何底层执行或库存事实。 */
    WorkOrderLifecycle recordProgress(UUID workOrderId, WorkOrderProgress progress,
                                      String idempotencyKey);

    /** 按必需工序、报工、质检和入库约束正常完成工单。 */
    WorkOrderLifecycle complete(UUID workOrderId, String idempotencyKey);

    /** 以人工原因完成 Released 或 InProgress 工单，不补造业务事实。 */
    WorkOrderLifecycle manualComplete(UUID workOrderId, String reason, String idempotencyKey);

    /** 查询当前租户内的工单生命周期，跨租户对象不可见。 */
    Optional<WorkOrderLifecycle> find(UUID workOrderId);
}
