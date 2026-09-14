package com.ailearn.platform.core.inventory.application;

import java.util.List;

/** 有上限的低库存查询结果；truncated=true 时提示调用方缩小仓库范围。 */
public record LowStockPage(List<LowStockItem> records, boolean truncated) {
    public LowStockPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
