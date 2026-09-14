package com.ailearn.platform.core.ai.application;

/** 用于模型上下文裁剪的历史消息投影，不暴露其他会话字段。 */
public record AiStoredMessage(String role, String content) {
}
