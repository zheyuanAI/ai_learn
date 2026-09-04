package com.ailearn.platform.core.sales.domain;

import com.ailearn.platform.core.sales.exception.SalesOrderErrorCode;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import java.util.Locale;

/**
 * 销售订单生命周期状态；履约进度不属于此枚举。
 */
public enum SalesOrderStatus {
    Draft,
    Submitted,
    Approved,
    Completed;

    /**
     * 解析持久化状态，拒绝未知值。
     *
     * @param value 数据库状态
     * @return 生命周期状态
     */
    public static SalesOrderStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单状态不能为空");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "draft" -> Draft;
            case "submitted" -> Submitted;
            case "approved" -> Approved;
            case "completed" -> Completed;
            default -> throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单状态不合法");
        };
    }
}
