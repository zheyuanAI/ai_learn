package com.ailearn.platform.core.stocktake.dto;

import com.ailearn.platform.core.stocktake.domain.StocktakeStatus;

/** 盘点分页查询参数；所有筛选均在当前可信租户范围内执行。 */
public class StocktakePageQuery {
    private int page = 1;
    private int size = 20;
    private String keyword;
    private StocktakeStatus status;

    /** 将页码、页大小和文本筛选限制到服务端允许范围。 */
    public StocktakePageQuery normalized() {
        StocktakePageQuery result = new StocktakePageQuery();
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
    public StocktakeStatus getStatus() { return status; }
    public void setStatus(StocktakeStatus status) { this.status = status; }
}
