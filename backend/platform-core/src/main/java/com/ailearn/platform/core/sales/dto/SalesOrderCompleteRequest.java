package com.ailearn.platform.core.sales.dto;

/**
 * 人工完成销售订单请求。
 */
public class SalesOrderCompleteRequest {
    private String completionReason;

    public String getCompletionReason() { return completionReason; }
    public void setCompletionReason(String completionReason) { this.completionReason = completionReason; }
}
