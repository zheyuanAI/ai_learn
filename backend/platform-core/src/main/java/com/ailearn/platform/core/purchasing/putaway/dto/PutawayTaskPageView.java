package com.ailearn.platform.core.purchasing.putaway.dto;

import java.util.List;

/**
 * 上架任务分页响应，字段与管理端查询约定保持一致。
 */
public record PutawayTaskPageView(List<PutawayTaskView> records, long total, int page, int size) {

    /**
     * 固化分页记录集合。
     */
    public PutawayTaskPageView {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
