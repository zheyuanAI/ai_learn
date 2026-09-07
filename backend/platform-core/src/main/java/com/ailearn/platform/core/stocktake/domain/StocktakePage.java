package com.ailearn.platform.core.stocktake.domain;

import java.util.List;

/** 盘点查询的领域分页结果。 */
public record StocktakePage(List<StocktakeOrder> records, long total) {

    /** 规范化分页集合，避免空值进入 HTTP 响应。 */
    public StocktakePage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
