package com.ailearn.platform.core.purchasing.domain;

/**
 * 到货验收单状态；Task 10 只在确认时生成 Confirmed 事实。
 */
public enum PurchaseReceiptStatus {
    Draft,
    Confirmed
}
