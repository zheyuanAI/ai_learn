package com.ailearn.platform.core.purchasing.domain;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import java.util.Locale;

/**
 * 采购订单生命周期状态。
 */
public enum PurchaseOrderStatus {
    Draft,
    Submitted,
    Approved,
    PartiallyReceived,
    Completed;

    /**
     * 解析数据库状态，拒绝未知状态。
     *
     * @param value 数据库状态文本
     * @return 采购订单状态
     */
    public static PurchaseOrderStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "采购订单状态不能为空");
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            try {
                return valueOf(value.trim().toLowerCase(Locale.ROOT).substring(0, 1).toUpperCase(Locale.ROOT)
                        + value.trim().toLowerCase(Locale.ROOT).substring(1));
            } catch (IllegalArgumentException | StringIndexOutOfBoundsException ignored) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "采购订单状态不合法");
            }
        }
    }
}
