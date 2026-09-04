package com.ailearn.platform.core.sales.dto;

import com.ailearn.platform.core.sales.domain.FulfillmentStatus;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import java.util.UUID;

/**
 * 销售订单查询参数。
 */
public class SalesOrderPageQuery {
    private String keyword;
    private SalesOrderStatus status;
    private UUID customerId;
    private FulfillmentStatus fulfillmentStatus;
    private Integer page = 1;
    private Integer size = 20;

    public com.ailearn.platform.core.sales.domain.SalesOrderPageQuery normalized() {
        return new com.ailearn.platform.core.sales.domain.SalesOrderPageQuery(keyword, status, customerId,
                fulfillmentStatus, page == null ? 1 : page, size == null ? 20 : size);
    }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public SalesOrderStatus getStatus() { return status; }
    public void setStatus(SalesOrderStatus status) { this.status = status; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public FulfillmentStatus getFulfillmentStatus() { return fulfillmentStatus; }
    public void setFulfillmentStatus(FulfillmentStatus fulfillmentStatus) { this.fulfillmentStatus = fulfillmentStatus; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
