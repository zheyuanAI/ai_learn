package com.ailearn.platform.core.purchasing.application;

import com.ailearn.platform.core.purchasing.dto.PurchaseOrderCompleteRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageResult;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderSaveRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderView;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptConfirmRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptView;
import java.util.UUID;

/**
 * 采购订单与到货验收应用端口。
 */
public interface PurchaseOrderApplicationService {

    /**
     * 查询当前租户采购订单。
     */
    PurchaseOrderPageResult page(com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageQuery query);

    /**
     * 查询当前租户采购订单详情。
     */
    PurchaseOrderView detail(UUID id);

    /**
     * 创建 Draft 采购订单。
     */
    PurchaseOrderView create(PurchaseOrderSaveRequest request, String idempotencyKey);

    /**
     * 修改 Draft 采购订单。
     */
    PurchaseOrderView update(UUID id, PurchaseOrderSaveRequest request, String idempotencyKey);

    /**
     * 提交采购订单。
     */
    PurchaseOrderView submit(UUID id, String idempotencyKey);

    /**
     * 审核采购订单。
     */
    PurchaseOrderView approve(UUID id, String idempotencyKey);

    /**
     * 人工完成采购订单。
     */
    PurchaseOrderView manuallyComplete(UUID id, PurchaseOrderCompleteRequest request, String idempotencyKey);

    /**
     * 确认到货验收并将实际接收数量送入质量隔离位。
     */
    PurchaseReceiptView confirmReceipt(UUID receiptId, PurchaseReceiptConfirmRequest request,
                                       String idempotencyKey);
}
