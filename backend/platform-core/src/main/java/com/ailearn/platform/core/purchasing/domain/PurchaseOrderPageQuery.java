package com.ailearn.platform.core.purchasing.domain;

import com.ailearn.platform.shared.exception.ValidationException;

/**
 * 采购订单查询条件。
 */
public record PurchaseOrderPageQuery(String keyword, PurchaseOrderStatus status, int page, int size) {

    /**
     * 规范化查询参数，限制单页上限。
     */
    public PurchaseOrderPageQuery {
        if (page < 1 || size < 1 || size > 200) {
            throw new ValidationException("分页参数必须满足 page >= 1 且 1 <= size <= 200");
        }
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
