package com.ailearn.platform.core.purchasing.domain;

import java.util.UUID;

/**
 * 采购应用读取的最小库位主数据事实。
 */
public record PurchasingLocationFact(UUID id, UUID tenantId, UUID warehouseId, String type, String status) {
}
