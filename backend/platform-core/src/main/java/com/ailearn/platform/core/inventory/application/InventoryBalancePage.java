package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import java.util.List;

/**
 * 库存余额分页结果。
 *
 * @param content 当前页数据
 * @param total 总条数
 * @param page 1-based 页码
 * @param size 每页条数
 */
public record InventoryBalancePage(List<InventoryBalance> content, long total, int page, int size) {

    /**
     * 规范化分页集合。
     */
    public InventoryBalancePage {
        content = content == null ? List.of() : List.copyOf(content);
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
