package com.ailearn.platform.core.ai.dto;

import com.ailearn.platform.core.ai.domain.NavigationAction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** 非流式接口及流式 done 事件共用的最终回答。 */
public record AiChatResponse(@JsonProperty("request_id") String requestId,
                             @JsonProperty("session_id") UUID sessionId,
                             String answer,
                             @JsonProperty("source_summary") String sourceSummary,
                             @JsonProperty("time_range_summary") String timeRangeSummary,
                             @JsonProperty("tool_call_summary") String toolCallSummary,
                             @JsonProperty("navigation_actions") List<NavigationAction> navigationActions,
                             @JsonProperty("model_id") String modelId,
                             @JsonProperty("input_tokens") int inputTokens,
                             @JsonProperty("output_tokens") int outputTokens,
                             String status) {
}
