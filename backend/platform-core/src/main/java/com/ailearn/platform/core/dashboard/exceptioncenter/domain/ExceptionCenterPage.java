package com.ailearn.platform.core.dashboard.exceptioncenter.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** 异常中心统一分页响应。 */
public record ExceptionCenterPage(@JsonProperty("records") List<ExceptionCenterRecord> records,
                                  long total, int page, int size,
                                  @JsonProperty("generated_at") Instant generatedAt,
                                  @JsonProperty("source_updated_at") Instant sourceUpdatedAt) {
    public ExceptionCenterPage {
        records = records == null ? List.of() : List.copyOf(records);
    }

    /** 返回总页数。 */
    public long getTotalPages() {
        return size <= 0 ? 0 : (total + size - 1) / size;
    }
}
