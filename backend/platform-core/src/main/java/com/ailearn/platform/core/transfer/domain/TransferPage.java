package com.ailearn.platform.core.transfer.domain;

import java.util.List;

/**
 * 调拨查询的领域分页结果；只承载当前页聚合和租户内总数，不参与库存写入。
 */
public record TransferPage(List<TransferOrder> records, long total) {

    /** 规范化分页集合，避免空值泄漏到 HTTP 响应。 */
    public TransferPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
