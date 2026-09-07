package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 库存余额分页结果。
 *
 * @param records 当前页数据
 * @param total 总条数
 * @param page 1-based 页码
 * @param size 每页条数
 */
public record InventoryBalancePage(@JsonProperty("records") List<InventoryBalance> records,
                                   long total, int page, int size) {

    /**
     * 规范化分页集合。
     */
    public InventoryBalancePage {
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** 保留内部库存快照调用方的 content() 兼容访问器；HTTP 只输出 records。 */
    @JsonIgnore
    public List<InventoryBalance> content() {
        return records;
    }

    /** 统一分页响应中的总页数。 */
    public long getTotalPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }

    /**
     * 判断是否还有下一页。
     *
     * @return 存在下一页时为 true
     */
    public boolean hasNext() {
        return page > 0 && size > 0 && (long) page * size < total;
    }
}
