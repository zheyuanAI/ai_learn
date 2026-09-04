package com.ailearn.platform.core.purchasing.domain;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 采购订单聚合；生命周期、人工完成审计和累计实际接收数量均由领域对象维护。
 */
public record PurchaseOrder(UUID id, UUID tenantId, String poNo, UUID supplierId,
                            java.time.LocalDate expectedArrivalDate, PurchaseOrderStatus status,
                            PurchaseCompletionType completionType, String completionReason,
                            UUID completedBy, String completedSessionId, OffsetDateTime completedAt,
                            String remark, long version, UUID createdBy, OffsetDateTime createdAt,
                            UUID updatedBy, OffsetDateTime updatedAt, List<PurchaseOrderLine> lines) {

    /**
     * 校验订单身份、明细租户和完成审计完整性。
     */
    public PurchaseOrder {
        if (id == null || tenantId == null || supplierId == null || expectedArrivalDate == null
                || status == null || poNo == null || poNo.isBlank() || poNo.trim().length() > 64
                || createdBy == null || version < 0 || lines == null || lines.isEmpty()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "采购订单必要字段不完整");
        }
        poNo = poNo.trim();
        lines = List.copyOf(lines);
        Set<Integer> lineNos = new HashSet<>();
        for (PurchaseOrderLine line : lines) {
            if (line == null || !tenantId.equals(line.tenantId()) || !lineNos.add(line.lineNo())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "采购订单明细必须同租户且行号唯一");
            }
        }
        if (status == PurchaseOrderStatus.Completed && completionType == null) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "已完成采购订单必须有完成方式");
        }
        if (status != PurchaseOrderStatus.Completed && completionType != null) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "未完成采购订单不能设置完成方式");
        }
        if (completionType == PurchaseCompletionType.Manual
                && (completionReason == null || completionReason.isBlank()
                || completedBy == null || completedSessionId == null || completedSessionId.isBlank()
                || completedAt == null)) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "人工完成必须记录原因和可信审计");
        }
    }

    /**
     * 将草稿提交。
     *
     * @param operatorId 可信操作人
     * @param at 操作时间
     * @return Submitted 订单
     */
    public PurchaseOrder submit(UUID operatorId, OffsetDateTime at) {
        requireStatus(PurchaseOrderStatus.Draft, "只有 Draft 采购单允许提交");
        return changed(PurchaseOrderStatus.Submitted, null, null, null, null, null, operatorId, at, lines);
    }

    /**
     * 将已提交采购单审核为 Approved。
     *
     * @param operatorId 可信操作人
     * @param at 操作时间
     * @return Approved 订单
     */
    public PurchaseOrder approve(UUID operatorId, OffsetDateTime at) {
        requireStatus(PurchaseOrderStatus.Submitted, "只有 Submitted 采购单允许审核");
        return changed(PurchaseOrderStatus.Approved, null, null, null, null, null, operatorId, at, lines);
    }

    /**
     * 应用本次实际接收数量；全部订单数量收齐时进入正常完成，否则进入部分收货。
     *
     * @param receivedDeltas 按采购明细 ID 汇总的实际接收数量
     * @param operatorId 可信操作人
     * @param at 操作时间
     * @return 累计数量和生命周期均更新后的订单
     */
    public PurchaseOrder applyReceipt(java.util.Map<UUID, java.math.BigDecimal> receivedDeltas,
                                      UUID operatorId, OffsetDateTime at) {
        return applyReceipt(receivedDeltas, operatorId, null, at);
    }

    /**
     * 应用本次实际接收数量并记录正常完成的可信会话审计。
     *
     * @param receivedDeltas 按采购明细 ID 汇总的实际接收数量
     * @param operatorId 可信操作人
     * @param sessionId 可信会话 JTI
     * @param at 操作时间
     * @return 累计数量和生命周期均更新后的订单
     */
    public PurchaseOrder applyReceipt(java.util.Map<UUID, java.math.BigDecimal> receivedDeltas,
                                      UUID operatorId, String sessionId, OffsetDateTime at) {
        if (status != PurchaseOrderStatus.Approved && status != PurchaseOrderStatus.PartiallyReceived) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "只有 Approved 或 PartiallyReceived 采购单允许收货");
        }
        List<PurchaseOrderLine> updatedLines = lines.stream()
                .map(line -> line.received(receivedDeltas.getOrDefault(line.id(), java.math.BigDecimal.ZERO)))
                .toList();
        boolean fullyReceived = updatedLines.stream().allMatch(line -> line.pendingQty().signum() == 0);
        PurchaseOrderStatus nextStatus = fullyReceived ? PurchaseOrderStatus.Completed : PurchaseOrderStatus.PartiallyReceived;
        return new PurchaseOrder(id, tenantId, poNo, supplierId, expectedArrivalDate, nextStatus,
                fullyReceived ? PurchaseCompletionType.Normal : null, null,
                fullyReceived ? operatorId : null, fullyReceived ? sessionId : null,
                fullyReceived ? at : null, remark,
                version + 1, createdBy, createdAt, operatorId, at, updatedLines);
    }

    /**
     * 人工完成采购单，只终止剩余待收数量，不生成收货或库存事实。
     *
     * @param reason 人工完成原因
     * @param operatorId 可信操作人
     * @param sessionId 可信会话 JTI
     * @param at 完成时间
     * @return 已完成人工采购单
     */
    public PurchaseOrder manuallyComplete(String reason, UUID operatorId, String sessionId, OffsetDateTime at) {
        if (status != PurchaseOrderStatus.Approved && status != PurchaseOrderStatus.PartiallyReceived) {
            throw new PurchasingException(PurchasingErrorCode.PO_001,
                    "只有 Approved 或 PartiallyReceived 采购单允许人工完成");
        }
        if (reason == null || reason.trim().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "人工完成原因不能为空");
        }
        return changed(PurchaseOrderStatus.Completed, PurchaseCompletionType.Manual, reason.trim(),
                operatorId, sessionId, at, operatorId, at, lines);
    }

    /**
     * 修改 Draft 字段；草稿不会存在累计收货事实。
     */
    public PurchaseOrder draftUpdated(UUID supplierId, java.time.LocalDate expectedArrivalDate,
                                      String remark, List<PurchaseOrderLine> updatedLines,
                                      UUID operatorId, OffsetDateTime at) {
        requireStatus(PurchaseOrderStatus.Draft, "只有 Draft 采购单允许修改");
        if (updatedLines == null || updatedLines.isEmpty()
                || updatedLines.stream().anyMatch(line -> line.receivedQty().signum() != 0)) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "Draft 采购单明细不完整或已包含收货事实");
        }
        return new PurchaseOrder(id, tenantId, poNo, supplierId, expectedArrivalDate, status,
                null, null, null, null, null, remark, version + 1, createdBy, createdAt,
                operatorId, at, updatedLines);
    }

    /**
     * 判断订单是否仍允许收货。
     */
    public boolean receivingAllowed() {
        return status == PurchaseOrderStatus.Approved || status == PurchaseOrderStatus.PartiallyReceived;
    }

    private void requireStatus(PurchaseOrderStatus expected, String message) {
        if (status != expected) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, message);
        }
    }

    private PurchaseOrder changed(PurchaseOrderStatus nextStatus, PurchaseCompletionType nextCompletionType,
                                  String nextReason, UUID nextCompletedBy, String nextSessionId,
                                  OffsetDateTime nextCompletedAt, UUID operatorId, OffsetDateTime at,
                                  List<PurchaseOrderLine> nextLines) {
        return new PurchaseOrder(id, tenantId, poNo, supplierId, expectedArrivalDate, nextStatus,
                nextCompletionType, nextReason, nextCompletedBy, nextSessionId, nextCompletedAt,
                remark, version + 1, createdBy, createdAt, operatorId, at, nextLines);
    }
}
