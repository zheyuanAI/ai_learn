package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 生产退料聚合根，对应 V5 mes_material_return 及其明细表。 */
public record MaterialReturn(UUID id, UUID tenantId, String returnNo, UUID workOrderId,
                             MaterialDocumentStatus status, List<MaterialReturnLine> lines,
                             UUID inventoryOperationId, UUID confirmedBy, String confirmedSessionId,
                             OffsetDateTime confirmedAt, UUID createdBy, OffsetDateTime createdAt,
                             UUID updatedBy, OffsetDateTime updatedAt) {

    public MaterialReturn {
        if (tenantId == null || id == null || workOrderId == null || createdBy == null || createdAt == null
                || returnNo == null || returnNo.isBlank() || returnNo.length() > 64
                || status == null || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("退料单头字段不合法");
        }
        lines = List.copyOf(lines);
        if (lines.stream().map(MaterialReturnLine::lineNo).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("退料明细行号不能重复");
        }
        if (status == MaterialDocumentStatus.Confirmed
                && (inventoryOperationId == null || confirmedBy == null || confirmedAt == null
                || confirmedSessionId == null || confirmedSessionId.isBlank()
                || lines.stream().anyMatch(line -> line.inventoryTransactionId() == null))) {
            throw new IllegalArgumentException("已确认退料必须保留库存和审计事实");
        }
    }

    /** 创建 Draft 退料单。 */
    public static MaterialReturn draft(UUID id, UUID tenantId, String returnNo, UUID workOrderId,
                                       List<MaterialReturnLine> lines, UUID userId, OffsetDateTime now) {
        return new MaterialReturn(id, tenantId, returnNo, workOrderId, MaterialDocumentStatus.Draft,
                lines, null, null, null, null, userId, now, userId, now);
    }

    /** 生成确认后的退料聚合。 */
    public MaterialReturn confirmed(List<UUID> transactionIds, UUID operationId, UUID userId,
                                   String sessionId, OffsetDateTime now) {
        if (transactionIds == null || transactionIds.size() != lines.size()) {
            throw new IllegalArgumentException("库存流水数量与退料明细不一致");
        }
        List<MaterialReturnLine> confirmedLines = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            confirmedLines.add(lines.get(i).confirmed(transactionIds.get(i)));
        }
        return new MaterialReturn(id, tenantId, returnNo, workOrderId, MaterialDocumentStatus.Confirmed,
                confirmedLines, operationId, userId, sessionId, now, createdBy, createdAt, userId, now);
    }
}
