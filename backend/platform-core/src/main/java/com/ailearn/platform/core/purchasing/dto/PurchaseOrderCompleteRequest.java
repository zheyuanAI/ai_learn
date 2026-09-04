package com.ailearn.platform.core.purchasing.dto;

/**
 * 采购订单人工完成请求。
 */
public class PurchaseOrderCompleteRequest {
    private String completionReason;

    public String getCompletionReason() { return completionReason; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }
}
