package com.ailearn.platform.core.ai.application;

/** 下游流式事件出口，Controller 可映射为 SSE，测试可映射为内存列表。 */
@FunctionalInterface
public interface AiStreamSink {
    void emit(String eventName, Object data);
}
