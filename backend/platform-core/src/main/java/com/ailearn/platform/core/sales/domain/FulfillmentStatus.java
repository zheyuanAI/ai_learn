package com.ailearn.platform.core.sales.domain;

/**
 * 由订单行累计数量派生的履约进度，不持久化。
 */
public enum FulfillmentStatus {
    NotStarted,
    InProgress,
    FullyShipped
}
