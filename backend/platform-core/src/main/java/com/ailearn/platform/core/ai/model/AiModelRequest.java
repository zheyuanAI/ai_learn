package com.ailearn.platform.core.ai.model;

import java.util.List;
import java.util.Map;

/** 模型无关的一轮 Responses 请求。 */
public record AiModelRequest(String instructions, List<Map<String, Object>> input,
                             List<AiToolDefinition> tools, int maxOutputTokens) {
    public AiModelRequest {
        instructions = instructions == null ? "" : instructions;
        input = input == null ? List.of() : List.copyOf(input);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (maxOutputTokens <= 0) {
            maxOutputTokens = 2048;
        }
    }
}
