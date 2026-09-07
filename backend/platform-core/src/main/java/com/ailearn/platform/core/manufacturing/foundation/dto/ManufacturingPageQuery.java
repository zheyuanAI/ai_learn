package com.ailearn.platform.core.manufacturing.foundation.dto;

/**
 * MES 基础事实共用分页查询参数；服务端将页大小限制为 200，避免一次读取无界数据。
 */
public class ManufacturingPageQuery {
    private int page = 1;
    private int size = 20;
    private String keyword;
    private String status;

    /** 规范化页码、页大小和文本筛选条件。 */
    public ManufacturingPageQuery normalized() {
        ManufacturingPageQuery result = new ManufacturingPageQuery();
        result.page = page < 1 ? 1 : page;
        result.size = size < 1 ? 20 : Math.min(size, 200);
        result.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        result.status = status == null || status.isBlank() ? null : status.trim();
        return result;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
