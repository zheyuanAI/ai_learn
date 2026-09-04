package com.ailearn.platform.core.purchasing.domain;

import java.util.UUID;

/**
 * 采购应用读取的最小商品主数据事实。
 */
public record PurchasingProductFact(UUID id, UUID tenantId, String uom, boolean batchManaged) {
}
