package com.ailearn.platform.core.masterdata.dto;

import java.util.List;

/**
 * 主数据分页响应。
 *
 * @param <T> 具体主数据视图类型
 */
public class MasterDataPageResult<T> {

    private final List<T> records;
    private final long total;
    private final long page;
    private final long size;
    private final long totalPages;

    /**
     * 创建分页结果并计算总页数。
     *
     * @param records 当前页记录
     * @param total   租户内符合条件的总记录数
     * @param page    当前页码
     * @param size    每页大小
     */
    public MasterDataPageResult(List<T> records, long total, long page, long size) {
        this.records = records == null ? List.of() : List.copyOf(records);
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public long getPage() {
        return page;
    }

    public long getSize() {
        return size;
    }

    public long getTotalPages() {
        return totalPages;
    }
}
