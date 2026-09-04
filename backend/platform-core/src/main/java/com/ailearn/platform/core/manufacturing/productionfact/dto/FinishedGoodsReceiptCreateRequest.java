package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** 创建 Draft 成品入库请求；产品从同租户工单读取。 */
public record FinishedGoodsReceiptCreateRequest(String receiptNo, UUID workOrderId,
                                                BigDecimal receiptQty, UUID warehouseId,
                                                UUID locationId) {
}
