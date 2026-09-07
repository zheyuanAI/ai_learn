package com.ailearn.platform.core.transfer.dto;

import com.ailearn.platform.core.transfer.domain.TransferStatus;

/**
 * 调拨分页查询参数；页码从 1 开始，状态和单号关键词均为租户内白名单筛选。
 */
public class TransferPageQuery {
    private int page = 1;
    private int size = 20;
    private String keyword;
    private TransferStatus status;

    /** 将页码、页大小和文本筛选规范化后交给应用服务。 */
    public TransferPageQuery normalized() {
        TransferPageQuery result = new TransferPageQuery();
        result.page = page < 1 ? 1 : page;
        result.size = size < 1 ? 20 : Math.min(size, 200);
        result.keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        result.status = status;
        return result;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }
}
