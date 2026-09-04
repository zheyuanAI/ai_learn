package com.ailearn.platform.core.purchasing.dto;

import com.ailearn.platform.core.purchasing.domain.PurchaseOrderStatus;

/**
 * 采购订单分页查询参数。
 */
public class PurchaseOrderPageQuery {
    private String keyword;
    private PurchaseOrderStatus status;
    private Integer page = 1;
    private Integer size = 20;

    /**
     * 转换为经过边界校验的领域查询条件。
     */
    public com.ailearn.platform.core.purchasing.domain.PurchaseOrderPageQuery normalized() {
        return new com.ailearn.platform.core.purchasing.domain.PurchaseOrderPageQuery(keyword, status,
                page == null ? 1 : page, size == null ? 20 : size);
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public PurchaseOrderStatus getStatus() { return status; }
    public void setStatus(PurchaseOrderStatus status) { this.status = status; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
