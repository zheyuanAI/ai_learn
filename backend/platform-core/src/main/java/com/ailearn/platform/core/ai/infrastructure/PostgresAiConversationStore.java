package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.application.AiConversationStore;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiStoredMessage;
import com.ailearn.platform.core.ai.dto.AiChatResponse;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 使用 PostgreSQL 保存当前租户、当前用户自己的 AI 会话和消息。 */
@Repository
public class PostgresAiConversationStore implements AiConversationStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 注入数据库访问器与统一 JSON 映射器。 */
    public PostgresAiConversationStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID openSession(AiRequestContext context, UUID requestedSessionId) {
        if (requestedSessionId != null) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM ai_chat_session
                    WHERE id = ? AND tenant_id = ? AND user_id = ? AND isdel = 0
                    """, Integer.class, requestedSessionId, context.tenantId(), context.userId());
            if (count == null || count == 0) {
                throw new AiException(AiErrorCode.AI_AUTH_001, "会话不存在或当前用户无权访问");
            }
            return requestedSessionId;
        }
        UUID id = UUID.randomUUID();
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        String sessionNo = "AI-" + timestamp + "-" + id.toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO ai_chat_session(id, tenant_id, session_no, user_id, last_message_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id, context.tenantId(), sessionNo, context.userId());
        return id;
    }

    @Override
    public List<AiStoredMessage> recentMessages(AiRequestContext context, UUID sessionId, int limit) {
        List<AiStoredMessage> newestFirst = jdbcTemplate.query("""
                SELECT role, content FROM ai_chat_message
                WHERE tenant_id = ? AND session_id = ? AND isdel = 0 AND status = 'Completed'
                ORDER BY created_at DESC, id DESC LIMIT ?
                """, (rs, rowNum) -> new AiStoredMessage(rs.getString("role"), rs.getString("content")),
                context.tenantId(), sessionId, Math.max(1, Math.min(limit, 20)));
        List<AiStoredMessage> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    public void saveUserMessage(AiRequestContext context, UUID sessionId, String content) {
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_chat_message(id, tenant_id, session_id, role, content, status)
                VALUES (?, ?, ?, 'User', ?, 'Completed')
                """, messageId, context.tenantId(), sessionId, content);
        incrementQuestionCount(context, sessionId);
    }

    @Override
    public UUID startAssistantMessage(AiRequestContext context, UUID sessionId, String modelId) {
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ai_chat_message(id, tenant_id, session_id, role, content, model_id, status)
                VALUES (?, ?, ?, 'Assistant', '', ?, 'Streaming')
                """, messageId, context.tenantId(), sessionId, modelId);
        return messageId;
    }

    @Override
    public void completeAssistantMessage(AiRequestContext context, UUID messageId, AiChatResponse response) {
        jdbcTemplate.update("""
                UPDATE ai_chat_message
                SET content = ?, source_summary = ?, time_range_summary = ?, tool_call_summary = ?,
                    navigation_action_summary = ?, model_id = ?, status = 'Completed',
                    input_tokens = ?, output_tokens = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND isdel = 0
                """, response.answer(), response.sourceSummary(), response.timeRangeSummary(),
                response.toolCallSummary(), json(response.navigationActions()), response.modelId(),
                response.inputTokens(), response.outputTokens(), messageId, context.tenantId());
        touchSession(context, response.sessionId());
    }

    @Override
    public void failAssistantMessage(AiRequestContext context, UUID messageId, String partialContent,
                                     String modelId, String status) {
        String safeStatus = "Interrupted".equals(status) ? "Interrupted" : "Failed";
        jdbcTemplate.update("""
                UPDATE ai_chat_message
                SET content = ?, model_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND isdel = 0
                """, partialContent == null ? "" : partialContent, modelId, safeStatus,
                messageId, context.tenantId());
    }

    private void incrementQuestionCount(AiRequestContext context, UUID sessionId) {
        jdbcTemplate.update("""
                UPDATE ai_chat_session SET question_count = question_count + 1,
                    last_message_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND user_id = ? AND isdel = 0
                """, sessionId, context.tenantId(), context.userId());
    }

    private void touchSession(AiRequestContext context, UUID sessionId) {
        jdbcTemplate.update("""
                UPDATE ai_chat_session SET last_message_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND user_id = ? AND isdel = 0
                """, sessionId, context.tenantId(), context.userId());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }
}
