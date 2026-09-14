package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Open WebUI 今日运营日报工具的严格参数对象。 */
@Schema(name = "AiDailyOperationReportToolRequest",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiDailyOperationReportToolRequest(
        @JsonProperty("time_range")
        @Schema(description = "日报固定使用系统当前自然日", allowableValues = {"today"},
                defaultValue = "today")
        String timeRange) {

    /** 转为固定工具参数并省略空值。 */
    public Map<String, Object> toArguments() {
        return timeRange == null || timeRange.isBlank()
                ? Map.of() : Map.of("time_range", timeRange.trim());
    }
}
