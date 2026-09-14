package com.ailearn.platform.core.ai.model;

/** 模型请求调用一个已注册 function 工具。 */
public record AiFunctionCall(String callId, String name, String arguments) {
    public AiFunctionCall {
        if (callId == null || callId.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("模型工具调用必须包含 callId 和 name");
        }
        arguments = arguments == null || arguments.isBlank() ? "{}" : arguments;
    }
}
