package com.ailearn.platform.core.ai.model;

import java.util.Map;

/** 发送给模型的只读 function 工具定义。 */
public record AiToolDefinition(String name, String description, Map<String, Object> parameters) {
    public AiToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工具定义名称不能为空");
        }
        description = description == null ? "" : description;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
