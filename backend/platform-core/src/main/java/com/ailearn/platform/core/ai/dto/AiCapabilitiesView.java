package com.ailearn.platform.core.ai.dto;

import com.ailearn.platform.core.ai.domain.AiActionCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 当前登录用户可用的 AI 只读能力视图。 */
public record AiCapabilitiesView(@JsonProperty("provider_enabled") boolean providerEnabled,
                                 @JsonProperty("model_id") String modelId,
                                 boolean streaming,
                                 List<String> tools,
                                 @JsonProperty("navigation_actions") List<AiActionCode> navigationActions) {
}
