package com.ailearn.platform.core.manufacturing.execution.domain;

import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 工单生命周期聚合。
 * <p>
 * 基础生产意图复用 foundation 的 {@link WorkOrderFact}；本聚合只追加审核、完成审计、版本锁定和执行汇总，
 * 不复制 BOM、Routing 或库存事实，也不让工单状态代替工序执行状态。
 * </p>
 */
public record WorkOrderLifecycle(
        WorkOrderFact workOrder,
        WorkOrderStatus status,
        Set<UUID> requiredOperationIds,
        WorkOrderProgress progress,
        String lockedBomVersion,
        String lockedRoutingVersion,
        UUID submittedBy,
        OffsetDateTime submittedAt,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        String rejectionReason,
        WorkOrderCompletionType completionType,
        String completionReason,
        UUID completedBy,
        String completedSessionId,
        OffsetDateTime completedAt) {

    public WorkOrderLifecycle {
        if (workOrder == null || status == null || progress == null) {
            throw new IllegalArgumentException("工单生命周期基础字段不能为空");
        }
        if (requiredOperationIds == null || requiredOperationIds.isEmpty()) {
            throw new IllegalArgumentException("工单必须保留至少一道必需工序");
        }
        requiredOperationIds = Set.copyOf(requiredOperationIds);
        if (workOrder.status() != status) {
            throw new IllegalArgumentException("生命周期状态必须与基础工单状态一致");
        }
        if ((lockedBomVersion == null) != (lockedRoutingVersion == null)) {
            throw new IllegalArgumentException("BOM 与 Routing 版本必须同时锁定");
        }
        if (status == WorkOrderStatus.Completed
                && (completionType == null || completedBy == null || completedAt == null)) {
            throw new IllegalArgumentException("已完成工单必须保存完成审计");
        }
    }

    /** 根据 foundation 工单创建未提交的生命周期聚合。 */
    public static WorkOrderLifecycle initial(WorkOrderFact workOrder, Set<UUID> requiredOperationIds) {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.Draft), WorkOrderStatus.Draft, requiredOperationIds,
                WorkOrderProgress.empty(), null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    /** 返回当前状态允许的工单命令，供接口层或后续查询端口直接使用。 */
    public Set<String> allowedActions() {
        Set<String> actions = new LinkedHashSet<>();
        switch (status) {
            case Draft, Rejected -> actions.add("submit");
            case PendingApproval -> {
                actions.add("approve");
                actions.add("reject");
            }
            case Released -> {
                actions.add("startProduction");
                actions.add("manualComplete");
            }
            case InProgress -> {
                actions.add("complete");
                actions.add("manualComplete");
            }
            case Completed -> {
                // 已完成没有可重复执行的生命周期命令。
            }
        }
        return Set.copyOf(actions);
    }

    /** 生成提交审核后的新聚合，保留上一次拒绝原因作为审计信息。 */
    public WorkOrderLifecycle submitted(UUID userId, OffsetDateTime time) {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.PendingApproval), WorkOrderStatus.PendingApproval, requiredOperationIds,
                progress, lockedBomVersion, lockedRoutingVersion, userId, time, reviewedBy, reviewedAt,
                rejectionReason, completionType, completionReason, completedBy, completedSessionId, completedAt);
    }

    /** 生成审核通过后的新聚合并锁定当前 BOM/Routing 版本。 */
    public WorkOrderLifecycle approved(String bomVersion, String routingVersion,
                                       UUID userId, OffsetDateTime time) {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.Released), WorkOrderStatus.Released, requiredOperationIds, progress,
                bomVersion, routingVersion, submittedBy, submittedAt, userId, time, rejectionReason,
                completionType, completionReason, completedBy, completedSessionId, completedAt);
    }

    /** 生成审核拒绝后的新聚合并保存拒绝原因与审核人。 */
    public WorkOrderLifecycle rejected(String reason, UUID userId, OffsetDateTime time) {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.Rejected), WorkOrderStatus.Rejected, requiredOperationIds, progress,
                lockedBomVersion, lockedRoutingVersion, submittedBy, submittedAt, userId, time, reason,
                completionType, completionReason, completedBy, completedSessionId, completedAt);
    }

    /** 生成开始生产后的工单状态；实际开工事实由 OperationExecution 负责。 */
    public WorkOrderLifecycle inProgress() {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.InProgress), WorkOrderStatus.InProgress, requiredOperationIds, progress,
                lockedBomVersion, lockedRoutingVersion, submittedBy, submittedAt, reviewedBy, reviewedAt,
                rejectionReason, completionType, completionReason, completedBy, completedSessionId, completedAt);
    }

    /** 更新后续执行链路提供的累计进度，不改变工单状态。 */
    public WorkOrderLifecycle withProgress(WorkOrderProgress newProgress) {
        return new WorkOrderLifecycle(workOrder.withStatus(status), status, requiredOperationIds, newProgress,
                lockedBomVersion, lockedRoutingVersion, submittedBy, submittedAt, reviewedBy, reviewedAt,
                rejectionReason, completionType, completionReason, completedBy, completedSessionId, completedAt);
    }

    /** 生成按完整事实正常完成后的聚合。 */
    public WorkOrderLifecycle completedNormally(UUID userId, String sessionId, OffsetDateTime time) {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.Completed), WorkOrderStatus.Completed, requiredOperationIds, progress,
                lockedBomVersion, lockedRoutingVersion, submittedBy, submittedAt, reviewedBy, reviewedAt,
                rejectionReason, WorkOrderCompletionType.Normal, null, userId, sessionId, time);
    }

    /** 生成授权人工完成后的聚合，不改变已有执行、报工、质检或入库累计值。 */
    public WorkOrderLifecycle completedManually(String reason, UUID userId, String sessionId,
                                                OffsetDateTime time) {
        return new WorkOrderLifecycle(workOrder.withStatus(WorkOrderStatus.Completed), WorkOrderStatus.Completed, requiredOperationIds, progress,
                lockedBomVersion, lockedRoutingVersion, submittedBy, submittedAt, reviewedBy, reviewedAt,
                rejectionReason, WorkOrderCompletionType.Manual, reason, userId, sessionId, time);
    }
}
