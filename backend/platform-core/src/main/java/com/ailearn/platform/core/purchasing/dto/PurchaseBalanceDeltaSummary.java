package com.ailearn.platform.core.purchasing.dto;

import java.util.UUID;

/**
 * 收货确认对库存余额的摘要；全部拒收时 inventoryChanged 为 false。
 */
public record PurchaseBalanceDeltaSummary(String receivedQty, boolean inventoryChanged,
                                          UUID qualityHoldLocationId) {
}
