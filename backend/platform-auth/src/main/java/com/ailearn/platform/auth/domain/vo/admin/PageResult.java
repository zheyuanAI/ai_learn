package com.ailearn.platform.auth.domain.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页查询结果包装 VO。
 * <p>
 * 封装当前页码、页大小、总记录数、总页数及结果记录清单。
 * </p>
 *
 * @param <T> 数据记录泛型类型
 */
@Schema(description = "通用分页查询结果对象")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    @Schema(description = "当前页码", example = "1")
    private long page;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "10")
    private long size;

    /**
     * 总记录数
     */
    @Schema(description = "总记录数", example = "42")
    private long total;

    /**
     * 总页数
     */
    @Schema(description = "总页数", example = "5")
    private long totalPages;

    /**
     * 当前页数据记录列表
     */
    @Schema(description = "数据记录列表")
    private List<T> records;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(long page, long size, long total, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.records = records != null ? records : Collections.emptyList();
        this.totalPages = size > 0 ? (total + size - 1) / size : 0;
    }

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(page, size, total, records);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(page, size, 0, Collections.emptyList());
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

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(long totalPages) {
        this.totalPages = totalPages;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
