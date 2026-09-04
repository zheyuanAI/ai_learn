package com.ailearn.platform.core.quality.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 质量模块读取的采购收货明细事实，不承担采购写入职责。
 */
public record QualityReceiptLineFact(
        UUID id,
        UUID purchaseOrderLineId,
        UUID productId,
        String uom,
        BigDecimal receivedQty,
        String lotNo,
        UUID targetWarehouseId) {
}
