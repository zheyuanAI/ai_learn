package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;

/** AI 对话请求；租户、用户、模型和密钥均不允许由客户端传入。 */
public record AiChatRequest(String message,
                            @JsonProperty("session_id") UUID sessionId,
                            @JsonProperty("tool_whitelist") Set<String> toolWhitelist,
                            @JsonProperty("page_context") AiPageContext pageContext) {
    public AiChatRequest {
        message = message == null ? "" : message.trim();
        toolWhitelist = toolWhitelist == null ? Set.of() : Set.copyOf(toolWhitelist);
    }
}
