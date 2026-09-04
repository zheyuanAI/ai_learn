package com.ailearn.platform.core.sales.domain;

import com.ailearn.platform.core.sales.exception.SalesOrderErrorCode;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 销售订单聚合；状态迁移和双轴派生均集中在领域对象中。
 */
public record SalesOrder(UUID id, UUID tenantId, String soNo, UUID customerId,
                         java.time.LocalDate plannedShipDate, SalesOrderStatus status,
                         CompletionType completionType, String completionReason,
                         UUID completedBy, String completedSessionId, OffsetDateTime completedAt,
                         String remark, long version, UUID createdBy, OffsetDateTime createdAt,
                         UUID updatedBy, OffsetDateTime updatedAt, List<SalesOrderLine> lines) {

    public SalesOrder {
        if (id == null || tenantId == null || customerId == null || status == null
                || createdBy == null || soNo == null || soNo.isBlank() || soNo.trim().length() > 64
                || version < 0 || lines == null || lines.isEmpty()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单必要字段不完整");
        }
        lines = List.copyOf(lines);
        Set<Integer> lineNos = new HashSet<>();
        for (SalesOrderLine line : lines) {
            if (!tenantId.equals(line.tenantId()) || !lineNos.add(line.lineNo())) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_006, "订单明细必须属于当前租户且行号唯一");
            }
        }
        if (status == SalesOrderStatus.Completed && completionType == null) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "已完成销售订单必须有完成方式");
        }
        if (status != SalesOrderStatus.Completed && completionType != null) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "未完成销售订单不能设置完成方式");
        }
        if (completionType == CompletionType.Manual
                && (completionReason == null || completionReason.isBlank()
                || completedBy == null || completedSessionId == null || completedSessionId.isBlank()
                || completedAt == null)) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "人工完成必须记录原因和可信审计");
        }
    }

    /**
     * 由订单行数量派生履约状态。
     *
     * @return NotStarted、InProgress 或 FullyShipped
     */
    public FulfillmentStatus fulfillmentStatus() {
        boolean anyProgress = lines.stream().anyMatch(line -> line.reservedQty().signum() > 0
                || line.pickedQty().signum() > 0 || line.shippedQty().signum() > 0);
        if (lines.stream().allMatch(line -> line.shippedQty().compareTo(line.orderedQty()) == 0)) {
            return FulfillmentStatus.FullyShipped;
        }
        return anyProgress ? FulfillmentStatus.InProgress : FulfillmentStatus.NotStarted;
    }

    /**
     * 推进草稿到已提交。
     *
     * @param operatorId 可信操作人
     * @param at 操作时间
     * @return 新版本订单
     */
    public SalesOrder submit(UUID operatorId, OffsetDateTime at) {
        requireStatus(SalesOrderStatus.Draft, "只有 Draft 订单允许提交");
        return changed(SalesOrderStatus.Submitted, null, null, null, null, null, operatorId, at, lines);
    }

    /**
     * 推进已提交到已审核。
     *
     * @param operatorId 可信操作人
     * @param at 操作时间
     * @return 新版本订单
     */
    public SalesOrder approve(UUID operatorId, OffsetDateTime at) {
        requireStatus(SalesOrderStatus.Submitted, "只有 Submitted 订单允许审核");
        return changed(SalesOrderStatus.Approved, null, null, null, null, null, operatorId, at, lines);
    }

    /**
     * 记录人工完成审计并终止订单剩余履约；不调用库存端口。
     *
     * @param reason 人工完成原因
     * @param operatorId 可信操作人
     * @param sessionId 可信会话 JTI
     * @param at 完成时间
     * @return 已完成人工订单
     */
    public SalesOrder manuallyComplete(String reason, UUID operatorId, String sessionId, OffsetDateTime at) {
        requireStatus(SalesOrderStatus.Approved, "只有 Approved 订单允许人工完成");
        if (reason == null || reason.trim().isBlank()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "人工完成原因不能为空");
        }
        if (lines.stream().anyMatch(line -> line.shippingStagedQty().signum() > 0)) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_003,
                    "存在未发货暂存数量，必须先退回拣货后才能人工完成");
        }
        return changed(SalesOrderStatus.Completed, CompletionType.Manual, reason.trim(), operatorId,
                sessionId, at, operatorId, at, lines);
    }

    /**
     * 应用履约累计数量并保持销售订单生命周期；全部发货时同步进入正常完成。
     * 入参：履约后的订单行、操作人和时间；出参：新的销售订单聚合；流程：只允许 Approved，先校验
     * 订单行仍属于本订单，再按是否全部发货决定保持 Approved 或进入 Completed + Normal。
     *
     * @param updatedLines 履约后的订单行
     * @param operatorId 可信操作人
     * @param at 操作时间
     * @return 履约更新后的订单
     */
    public SalesOrder fulfillmentUpdated(List<SalesOrderLine> updatedLines,
                                         UUID operatorId,
                                         OffsetDateTime at) {
        requireStatus(SalesOrderStatus.Approved, "只有 Approved 订单允许履约操作");
        if (updatedLines == null || updatedLines.size() != lines.size()
                || updatedLines.stream().anyMatch(line -> line == null || !tenantId.equals(line.tenantId()))) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "履约订单行不完整");
        }
        boolean fullyShipped = updatedLines.stream()
                .allMatch(line -> line.shippedQty().compareTo(line.orderedQty()) == 0);
        return new SalesOrder(id, tenantId, soNo, customerId, plannedShipDate,
                fullyShipped ? SalesOrderStatus.Completed : SalesOrderStatus.Approved,
                fullyShipped ? CompletionType.Normal : null,
                null, null, null, null, remark, version + 1, createdBy, createdAt,
                operatorId, at, updatedLines);
    }

    /**
     * 为人工完成生成释放未拣预留后的订单聚合。
     * 入参：已把每行未拣预留释放后的订单行、原因和可信审计；出参：人工完成订单；流程：保持领域
     * 对未发货暂存数量的校验，再写入 Manual 完成审计，不补造库存事实。
     *
     * @param updatedLines 释放未拣预留后的订单行
     * @param reason 完成原因
     * @param operatorId 可信操作人
     * @param sessionId 可信会话
     * @param at 完成时间
     * @return 人工完成订单
     */
    public SalesOrder manuallyCompleteAfterRelease(List<SalesOrderLine> updatedLines,
                                                   String reason,
                                                   UUID operatorId,
                                                   String sessionId,
                                                   OffsetDateTime at) {
        SalesOrder released = new SalesOrder(id, tenantId, soNo, customerId, plannedShipDate, status,
                completionType, completionReason, completedBy, completedSessionId, completedAt, remark,
                version, createdBy, createdAt, updatedBy, updatedAt, updatedLines);
        return released.manuallyComplete(reason, operatorId, sessionId, at);
    }

    /**
     * 生成 Draft 的可编辑版本。
     */
    public SalesOrder draftUpdated(UUID customerId, java.time.LocalDate plannedShipDate,
                                   String remark, List<SalesOrderLine> updatedLines,
                                   UUID operatorId, OffsetDateTime at) {
        requireStatus(SalesOrderStatus.Draft, "只有 Draft 订单允许修改");
        return new SalesOrder(id, tenantId, soNo, customerId, plannedShipDate, status, completionType,
                completionReason, completedBy, completedSessionId, completedAt, remark, version + 1,
                createdBy, createdAt, operatorId, at, updatedLines);
    }

    private void requireStatus(SalesOrderStatus expected, String message) {
        if (status != expected) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_001, message);
        }
    }

    private SalesOrder changed(SalesOrderStatus newStatus, CompletionType newCompletionType,
                               String newReason, UUID newCompletedBy, String newSessionId,
                               OffsetDateTime newCompletedAt, UUID operatorId, OffsetDateTime at,
                               List<SalesOrderLine> newLines) {
        return new SalesOrder(id, tenantId, soNo, customerId, plannedShipDate, newStatus,
                newCompletionType, newReason, newCompletedBy, newSessionId, newCompletedAt,
                remark, version + 1, createdBy, createdAt, operatorId, at, newLines);
    }
}
