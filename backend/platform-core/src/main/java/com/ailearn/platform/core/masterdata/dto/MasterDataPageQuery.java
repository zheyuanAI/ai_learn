package com.ailearn.platform.core.masterdata.dto;

import java.util.UUID;

/**
 * 六类主数据共用分页查询参数。
 * <p>
 * 用途：提供租户内分页、关键词、状态及少量安全白名单筛选条件。
 * 入参：HTTP 使用 camelCase 字段；未提供页码或页大小时使用 1/20。
 * 出参：经 {@link #normalized()} 限制范围后的不可变查询对象。
 * 流程：Controller 绑定后由应用服务规范化，再交给 Repository 构造租户范围 SQL。
 * </p>
 */
public class MasterDataPageQuery {

    private long page = 1;
    private long size = 20;
    private String keyword;
    private String status;
    private String sortField;
    private String sortOrder;
    private UUID warehouseId;
    private String type;
    private String category;
    private Boolean batchManaged;

    /**
     * 将分页参数和文本筛选条件规范化。
     *
     * @return 供应用层和基础设施层使用的标准查询参数
     */
    public MasterDataPageQuery normalized() {
        MasterDataPageQuery result = new MasterDataPageQuery();
        result.page = page < 1 ? 1 : page;
        result.size = size < 1 ? 20 : Math.min(size, 200);
        result.keyword = trimToNull(keyword);
        result.status = trimToNull(status);
        result.sortField = trimToNull(sortField);
        result.sortOrder = trimToNull(sortOrder);
        result.warehouseId = warehouseId;
        result.type = trimToNull(type);
        result.category = trimToNull(category);
        result.batchManaged = batchManaged;
        return result;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getBatchManaged() {
        return batchManaged;
    }

    public void setBatchManaged(Boolean batchManaged) {
        this.batchManaged = batchManaged;
    }
}
