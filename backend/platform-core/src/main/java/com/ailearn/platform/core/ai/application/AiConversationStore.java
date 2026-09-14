package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.dto.AiChatResponse;
import java.util.List;
import java.util.UUID;

/** AI 会话与消息持久化端口。 */
public interface AiConversationStore {
    UUID openSession(AiRequestContext context, UUID requestedSessionId);

    List<AiStoredMessage> recentMessages(AiRequestContext context, UUID sessionId, int limit);

    void saveUserMessage(AiRequestContext context, UUID sessionId, String content);

    UUID startAssistantMessage(AiRequestContext context, UUID sessionId, String modelId);

    void completeAssistantMessage(AiRequestContext context, UUID messageId, AiChatResponse response);

    void failAssistantMessage(AiRequestContext context, UUID messageId, String partialContent,
                              String modelId, String status);
}
