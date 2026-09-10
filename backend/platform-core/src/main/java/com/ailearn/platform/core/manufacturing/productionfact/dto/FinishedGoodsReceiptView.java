package com.ailearn.platform.core.manufacturing.productionfact.dto;

import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 成品入库查询视图。
 *
 * 用途：把成品入库内部事实转换成页面字段并返回服务端动作能力。
 * 入参：当前租户的成品入库事实；出参：入库事实字段和 allowedActions。
 * 流程：复制库存审计字段，按草稿状态和当前用户权限计算确认动作。
 */
public record FinishedGoodsReceiptView(
        UUID id,
        UUID tenantId,
        String receiptNo,
        UUID workOrderId,
        String workOrderNo,
        String productName,
        BigDecimal receiptQty,
        UUID warehouseId,
        String warehouseName,
        UUID locationId,
        String locationCode,
        FinishedGoodsReceiptStatus status,
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

    public FinishedGoodsReceiptView {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /**
     * 将成品入库事实转换为页面视图。
     * 入参：同租户成品入库聚合；出参：页面查询视图；流程：按状态计算确认动作。
     */
    public static FinishedGoodsReceiptView from(FinishedGoodsReceipt receipt) {
        return new FinishedGoodsReceiptView(receipt.id(), receipt.tenantId(), receipt.receiptNo(),
                receipt.workOrderId(), null, null, receipt.receiptQty(), receipt.warehouseId(), null,
                receipt.locationId(), null, receipt.status(), receipt.inventoryOperationId(),
                receipt.inventoryTransactionId(), receipt.confirmedBy(), receipt.confirmedSessionId(),
                receipt.confirmedAt(), receipt.createdBy(), receipt.createdAt(), receipt.updatedBy(),
                receipt.updatedAt(), actions(receipt.status()));
    }

    /** 草稿单只有仓库确认权限存在时才返回可执行的确认动作。 */
    private static List<AllowedActionVo> actions(FinishedGoodsReceiptStatus status) {
        if (status != FinishedGoodsReceiptStatus.Draft) {
            return List.of();
        }
        boolean enabled = UserContextHolder.hasPermission("mes:finished:confirm");
        return List.of(new AllowedActionVo("confirm", enabled,
                enabled ? null : "当前用户没有执行该操作的权限"));
    }
}
