package com.ailearn.platform.core.ai.domain;

/**
 * AI 可建议的页面导航动作白名单。
 * 这里只表达页面入口，不能执行任何业务命令。
 */
public enum AiActionCode {
    SALES_ORDER_DETAIL,
    SALES_ORDER_FULFILLMENT,
    PURCHASE_ORDER_DETAIL,
    PURCHASE_QUALITY_PROCESSING,
    WORK_ORDER_DETAIL,
    DEVICE_DETAIL,
    DEVICE_ALARM_DETAIL,
    INVENTORY_BALANCE,
    INVENTORY_RESERVATION,
    ROLE_PERMISSION_CONFIGURATION,
    TRACEABILITY
}
