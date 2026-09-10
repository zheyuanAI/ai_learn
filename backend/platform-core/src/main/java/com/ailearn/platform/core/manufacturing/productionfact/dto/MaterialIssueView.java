package com.ailearn.platform.core.manufacturing.productionfact.dto;

import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialDocumentStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssueLine;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 生产领料查询视图。
 *
 * 用途：把生产领料内部事实转换为页面使用的明细字段，并返回服务端计算的确认能力。
 * 入参：当前租户指定工单的领料事实；出参：页面单据字段、明细和 allowedActions。
 */
public record MaterialIssueView(
        UUID id,
        UUID tenantId,
        String issueNo,
        UUID workOrderId,
        String workOrderNo,
        MaterialDocumentStatus status,
        List<MaterialIssueLineView> items,
        UUID inventoryOperationId,
        UUID inventoryTransactionId,
        UUID confirmedBy,
        String confirmedSessionId,
        OffsetDateTime confirmedAt,
        UUID createdBy,
        OffsetDateTime createdAt,
        UUID updatedBy,
        OffsetDateTime updatedAt,
        String overageReason,
        List<AllowedActionVo> allowedActions) {

    public MaterialIssueView {
        items = items == null ? List.of() : List.copyOf(items);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /**
     * 将领料事实转换为页面视图。
     * 入参：当前租户的生产领料聚合；出参：页面明细字段和确认动作能力。
     */
    public static MaterialIssueView from(MaterialIssue issue) {
        List<MaterialIssueLineView> items = issue.lines().stream()
                .map(MaterialIssueLineView::from)
                .toList();
        UUID transactionId = issue.lines().stream()
                .map(MaterialIssueLine::inventoryTransactionId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new MaterialIssueView(issue.id(), issue.tenantId(), issue.issueNo(), issue.workOrderId(),
                null, issue.status(), items, issue.inventoryOperationId(), transactionId, issue.confirmedBy(),
                issue.confirmedSessionId(), issue.confirmedAt(), issue.createdBy(), issue.createdAt(),
                issue.updatedBy(), issue.updatedAt(), issue.overageReason(), actions(issue.status()));
    }

    /** 草稿单只有拥有确认权限的角色才返回可执行的确认动作。 */
    private static List<AllowedActionVo> actions(MaterialDocumentStatus status) {
        if (status != MaterialDocumentStatus.Draft) {
            return List.of();
        }
        boolean enabled = UserContextHolder.hasPermission("mes:material:confirm");
        return List.of(new AllowedActionVo("confirm", enabled,
                enabled ? null : "当前用户没有执行该操作的权限"));
    }

    /** 页面领料明细字段；主数据名称由页面已有目录按 ID 兜底显示。 */
    public record MaterialIssueLineView(
            UUID id,
            int lineNo,
            UUID productId,
            String productCode,
            String productName,
            String productSpec,
            UUID warehouseId,
            String warehouseName,
            UUID locationId,
            String locationCode,
            BigDecimal issueQty,
            String uom,
            UUID inventoryTransactionId) {

        /** 将领域领料明细转换为页面明细；不在 MES 查询中臆造主数据名称或单位。 */
        public static MaterialIssueLineView from(MaterialIssueLine line) {
            return new MaterialIssueLineView(line.id(), line.lineNo(), line.productId(), null, null, null,
                    line.warehouseId(), null, line.locationId(), null, line.issueQty(), null,
                    line.inventoryTransactionId());
        }
    }
}
