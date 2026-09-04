package com.ailearn.platform.core.purchasing.dto;

/**
 * 到货、拒收和实际接收汇总。
 */
public record PurchaseArrivalAcceptanceSummary(String arrivedQty, String rejectedQty, String receivedQty) {
}
