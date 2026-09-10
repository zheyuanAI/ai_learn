package com.ailearn.platform.core.manufacturing.productionfact.dto;

import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialDocumentStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturnLine;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 生产退料查询视图。
 *
 * 用途：把生产退料内部事实转换为页面使用的明细字段，并返回服务端计算的确认能力。
 * 入参：当前租户指定工单的退料事实；出参：页面单据字段、明细和 allowedActions。
 */
public record MaterialReturnView(
        UUID id,
        UUID tenantId,
        String returnNo,
        UUID workOrderId,
        String workOrderNo,
        MaterialDocumentStatus status,
        List<MaterialReturnLineView> items,
        UUID inventoryOperationId,
        UUID inventoryTransactionId,
        UUID confirmedBy,
        String confirmedSessionId,
        OffsetDateTime confirmedAt,
        UUID createdBy,
        OffsetDateTime createdAt,
        UUID updatedBy,
        OffsetDateTime updatedAt,
        List<AllowedActionVo> allowedActions) {

    public MaterialReturnView {
        items = items == null ? List.of() : List.copyOf(items);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /**
     * 将退料事实转换为页面视图。
     * 入参：当前租户的生产退料聚合；出参：页面明细字段和确认动作能力。
     */
    public static MaterialReturnView from(MaterialReturn value) {
        List<MaterialReturnLineView> items = value.lines().stream()
                .map(MaterialReturnLineView::from)
                .toList();
        UUID transactionId = value.lines().stream()
                .map(MaterialReturnLine::inventoryTransactionId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new MaterialReturnView(value.id(), value.tenantId(), value.returnNo(), value.workOrderId(), null,
                value.status(), items, value.inventoryOperationId(), transactionId, value.confirmedBy(),
                value.confirmedSessionId(), value.confirmedAt(), value.createdBy(), value.createdAt(),
                value.updatedBy(), value.updatedAt(), actions(value.status()));
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

    /** 页面退料明细字段；主数据名称由页面已有目录按 ID 兜底显示。 */
    public record MaterialReturnLineView(
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
            BigDecimal returnQty,
            String uom,
            UUID inventoryTransactionId) {

        /** 将领域退料明细转换为页面明细；不在 MES 查询中臆造主数据名称或单位。 */
        public static MaterialReturnLineView from(MaterialReturnLine line) {
            return new MaterialReturnLineView(line.id(), line.lineNo(), line.productId(), null, null, null,
                    line.warehouseId(), null, line.locationId(), null, line.returnQty(), null,
                    line.inventoryTransactionId());
        }
    }
}
