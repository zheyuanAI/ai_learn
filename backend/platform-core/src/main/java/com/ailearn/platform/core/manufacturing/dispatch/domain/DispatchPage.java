package com.ailearn.platform.core.manufacturing.dispatch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 派工列表统一分页响应。 */
public record DispatchPage(@JsonProperty("records") List<DispatchOrder> records,
                           long total, int page, int size) {
    public DispatchPage {
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** 返回总页数，供前端统一分页组件使用。 */
    public long getTotalPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }
}
