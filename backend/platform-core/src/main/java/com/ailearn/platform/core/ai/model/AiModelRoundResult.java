package com.ailearn.platform.core.ai.model;

import java.util.List;

/** 一轮流式模型响应聚合结果；正文增量同时通过监听器向下游转发。 */
public record AiModelRoundResult(String responseId, String modelId, String text,
                                 List<AiFunctionCall> functionCalls,
                                 int inputTokens, int outputTokens,
                                 AiModelStatus status, String errorMessage) {
    public AiModelRoundResult {
        responseId = responseId == null ? "" : responseId;
        modelId = modelId == null ? "" : modelId;
        text = text == null ? "" : text;
        functionCalls = functionCalls == null ? List.of() : List.copyOf(functionCalls);
        status = status == null ? AiModelStatus.Failed : status;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
