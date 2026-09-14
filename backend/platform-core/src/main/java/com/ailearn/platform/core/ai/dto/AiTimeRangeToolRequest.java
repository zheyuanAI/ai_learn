package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Open WebUI 时间范围摘要工具的严格参数对象。 */
@Schema(name = "AiTimeRangeToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiTimeRangeToolRequest(
        @JsonProperty("time_range")
        @Schema(description = "受控时间范围；具体可选值以对应工具 OpenAPI 为准",
                allowableValues = {"today", "7d", "30d"}, defaultValue = "today")
        String timeRange) {

    /** 转为固定工具参数并省略空值。 */
    public Map<String, Object> toArguments() {
        return timeRange == null || timeRange.isBlank()
                ? Map.of() : Map.of("time_range", timeRange.trim());
    }
}
