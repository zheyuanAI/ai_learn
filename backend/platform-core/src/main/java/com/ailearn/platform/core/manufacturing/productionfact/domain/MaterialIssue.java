package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 生产领料聚合根，对应 V5 mes_material_issue 及其明细表。 */
public record MaterialIssue(UUID id, UUID tenantId, String issueNo, UUID workOrderId,
                            MaterialDocumentStatus status, List<MaterialIssueLine> lines,
                            UUID inventoryOperationId, UUID confirmedBy, String confirmedSessionId,
                            OffsetDateTime confirmedAt, UUID createdBy, OffsetDateTime createdAt,
                            UUID updatedBy, OffsetDateTime updatedAt) {

    public MaterialIssue {
        requireHeader(tenantId, id, workOrderId, issueNo, createdBy, createdAt);
        if (status == null || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("领料状态和明细不能为空");
        }
        lines = List.copyOf(lines);
        if (lines.stream().map(MaterialIssueLine::lineNo).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("领料明细行号不能重复");
        }
        if (status == MaterialDocumentStatus.Confirmed
                && (inventoryOperationId == null || confirmedBy == null || confirmedAt == null
                || confirmedSessionId == null || confirmedSessionId.isBlank()
                || lines.stream().anyMatch(line -> line.inventoryTransactionId() == null))) {
            throw new IllegalArgumentException("已确认领料必须保留库存和审计事实");
        }
    }

    /** 创建 Draft 领料单。 */
    public static MaterialIssue draft(UUID id, UUID tenantId, String issueNo, UUID workOrderId,
                                      List<MaterialIssueLine> lines, UUID userId, OffsetDateTime now) {
        return new MaterialIssue(id, tenantId, issueNo, workOrderId, MaterialDocumentStatus.Draft,
                lines, null, null, null, null, userId, now, userId, now);
    }

    /** 生成确认后的领料聚合，不改变原始明细数量。 */
    public MaterialIssue confirmed(List<UUID> transactionIds, UUID operationId, UUID userId,
                                   String sessionId, OffsetDateTime now) {
        if (transactionIds == null || transactionIds.size() != lines.size()) {
            throw new IllegalArgumentException("库存流水数量与领料明细不一致");
        }
        List<MaterialIssueLine> confirmedLines = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            confirmedLines.add(lines.get(i).confirmed(transactionIds.get(i)));
        }
        return new MaterialIssue(id, tenantId, issueNo, workOrderId, MaterialDocumentStatus.Confirmed,
                confirmedLines, operationId, userId, sessionId, now, createdBy, createdAt, userId, now);
    }

    private static void requireHeader(UUID tenantId, UUID id, UUID workOrderId, String no,
                                      UUID userId, OffsetDateTime createdAt) {
        if (tenantId == null || id == null || workOrderId == null || userId == null || createdAt == null
                || no == null || no.isBlank() || no.length() > 64) {
            throw new IllegalArgumentException("领料单头字段不合法");
        }
    }
}
