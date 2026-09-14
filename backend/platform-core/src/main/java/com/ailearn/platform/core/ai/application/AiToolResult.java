package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.domain.NavigationAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 受控只读工具的标准结果和回答依据。 */
public record AiToolResult(@JsonProperty("tool_name") String toolName,
                           Object data,
                           @JsonProperty("source_summary") String sourceSummary,
                           @JsonProperty("time_range_summary") String timeRangeSummary,
                           @JsonProperty("navigation_actions") List<NavigationAction> navigationActions,
                           AiToolStatus status) {
    public AiToolResult {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        sourceSummary = sourceSummary == null ? "" : sourceSummary;
        timeRangeSummary = timeRangeSummary == null ? "" : timeRangeSummary;
        navigationActions = navigationActions == null ? List.of() : List.copyOf(navigationActions);
        status = status == null ? AiToolStatus.Success : status;
    }
}
