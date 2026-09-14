package com.ailearn.platform.core.ai.model;

import java.util.function.Consumer;

/** 可替换的 AI 模型 Provider 端口。 */
public interface AiModelClient {
    /**
     * 执行一轮流式 Responses 调用。
     * 入参：模型无关请求和最终回答增量监听器；出参：工具调用、Token 和终止状态聚合；流程：Provider 自行转换上游协议。
     */
    AiModelRoundResult stream(AiModelRequest request, Consumer<String> textDeltaConsumer);
}
