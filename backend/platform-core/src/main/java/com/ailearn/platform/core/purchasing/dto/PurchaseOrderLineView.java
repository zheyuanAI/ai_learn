package com.ailearn.platform.core.purchasing.dto;

import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLineCumulative;
import java.util.UUID;

/**
 * 采购订单明细响应，数量统一以六位小数字符串返回。
 */
public record PurchaseOrderLineView(UUID id, int lineNo, UUID productId, String uom,
                                    String orderedQty, String receivedQty, String pendingQty,
                                    UUID targetWarehouseId, UUID sourceWorkOrderId,
                                    String arrivedQty, String rejectedQty, String qualifiedQty,
                                    String releaseExecutedQty, String putawayQty) {

    /**
     * 保留原有九字段构造入口；入参为订单行基础字段，出参为累计未知的视图。
     * 流程：列表、写命令及既有调用方未读取履约事实时，以 null 明确表示未查询。
     */
    public PurchaseOrderLineView(UUID id, int lineNo, UUID productId, String uom,
                                 String orderedQty, String receivedQty, String pendingQty,
                                 UUID targetWarehouseId, UUID sourceWorkOrderId) {
        this(id, lineNo, productId, uom, orderedQty, receivedQty, pendingQty,
                targetWarehouseId, sourceWorkOrderId, null, null, null, null, null);
    }

    /**
     * 将领域明细转为 HTTP 视图。
     */
    public static PurchaseOrderLineView from(PurchaseOrderLine line) {
        // 修改用途：列表和写命令响应未查询跨域履约事实，未知值保持 null，不能伪造为零。
        return new PurchaseOrderLineView(line.id(), line.lineNo(), line.productId(), line.uom(),
                text(line.orderedQty()), text(line.receivedQty()), text(line.pendingQty()),
                line.targetWarehouseId(), line.sourceWorkOrderId());
    }

    /**
     * 将当前订单行及其真实累计转为详情 HTTP 视图；入参为订单行与聚合快照，出参为六位小数字符串。
     * 流程：保留订单原有已收/待收值，仅把有据可查的五类履约事实填入详情。
     */
    public static PurchaseOrderLineView from(PurchaseOrderLine line, PurchaseOrderLineCumulative cumulative) {
        return new PurchaseOrderLineView(line.id(), line.lineNo(), line.productId(), line.uom(),
                text(line.orderedQty()), text(line.receivedQty()), text(line.pendingQty()),
                line.targetWarehouseId(), line.sourceWorkOrderId(), text(cumulative.arrivedQty()),
                text(cumulative.rejectedQty()), text(cumulative.qualifiedQty()),
                text(cumulative.releaseExecutedQty()), text(cumulative.putawayQty()));
    }

    private static String text(java.math.BigDecimal value) {
        return value.setScale(6).toPlainString();
    }
}
