package com.ailearn.platform.core.manufacturing.operation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 工序执行列表统一分页响应。 */
public record OperationExecutionPage(@JsonProperty("records") List<OperationExecution> records,
                                     long total, int page, int size) {
    public OperationExecutionPage {
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** 返回总页数。 */
    public long getTotalPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }
}
