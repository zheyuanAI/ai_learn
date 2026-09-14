package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.dto.AiChatRequest;
import com.ailearn.platform.core.ai.dto.AiChatResponse;
import com.ailearn.platform.core.ai.dto.AiPageContext;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import com.ailearn.platform.core.ai.infrastructure.OpenWebUiAgentClient;
import com.ailearn.platform.core.ai.infrastructure.OpenWebUiAgentResult;
import com.ailearn.platform.core.ai.model.AiFunctionCall;
import com.ailearn.platform.core.ai.model.AiModelClient;
import com.ailearn.platform.core.ai.model.AiModelRequest;
import com.ailearn.platform.core.ai.model.AiModelRoundResult;
import com.ailearn.platform.core.ai.model.AiModelStatus;
import com.ailearn.platform.core.ai.model.AiToolDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 编排 WMS 会话、Open WebUI/DeepSeek Provider、受控只读工具和来源汇总。 */
@Service
public class AiChatOrchestrator {
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final AiProperties properties;
    private final AiModelClient modelClient;
    private final AiToolRegistry toolRegistry;
    private final AiToolExecutor toolExecutor;
    private final AiConversationStore conversationStore;
    private final AiPromptPolicy promptPolicy;
    private final ObjectMapper objectMapper;
    private final OpenWebUiAgentClient openWebUiAgentClient;

    /** 注入模型端口、工具治理、会话存储和统一 Prompt 策略。 */
    @Autowired
    public AiChatOrchestrator(AiProperties properties, AiModelClient modelClient,
                              AiToolRegistry toolRegistry, AiToolExecutor toolExecutor,
                              AiConversationStore conversationStore, AiPromptPolicy promptPolicy,
                              ObjectMapper objectMapper, OpenWebUiAgentClient openWebUiAgentClient) {
        this.properties = properties;
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.conversationStore = conversationStore;
        this.promptPolicy = promptPolicy;
        this.objectMapper = objectMapper;
        this.openWebUiAgentClient = openWebUiAgentClient;
    }

    /** 测试和直连 DeepSeek 兼容入口；Open WebUI 路径必须使用完整生产构造器。 */
    public AiChatOrchestrator(AiProperties properties, AiModelClient modelClient,
                              AiToolRegistry toolRegistry, AiToolExecutor toolExecutor,
                              AiConversationStore conversationStore, AiPromptPolicy promptPolicy,
                              ObjectMapper objectMapper) {
        this(properties, modelClient, toolRegistry, toolExecutor, conversationStore,
                promptPolicy, objectMapper, null);
    }

    /**
     * 用途：执行一次完整问答并持续发出下游事件。
     * 入参：可信上下文、聊天请求和事件出口；出参：最终回答；流程：会话落库、模型工具循环、来源汇总、消息收口。
     */
    public AiChatResponse chat(AiRequestContext context, AiChatRequest request, AiStreamSink sink) {
        validate(request);
        AiStreamSink safeSink = sink == null ? (event, data) -> { } : sink;
        UUID sessionId = conversationStore.openSession(context, request.sessionId());
        List<AiStoredMessage> history = conversationStore.recentMessages(context, sessionId, 12);
        conversationStore.saveUserMessage(context, sessionId, request.message());
        String configuredModel = configuredModel();
        UUID assistantMessageId = conversationStore.startAssistantMessage(context, sessionId, configuredModel);
        StringBuilder streamedText = new StringBuilder();
        safeSink.emit("meta", Map.of("request_id", context.requestId(), "session_id", sessionId,
                "model_id", configuredModel));

        try {
            validateWhitelist(context, request.toolWhitelist());
            List<AiReadTool> allowedTools = toolRegistry.availableTools(context, request.toolWhitelist());
            List<Map<String, Object>> input = buildInput(history, request);
            if (isOpenWebUiProvider()) {
                if (openWebUiAgentClient == null) {
                    throw new AiException(AiErrorCode.AI_PROVIDER_001, "Open WebUI 客户端未装配");
                }
                OpenWebUiAgentResult result = openWebUiAgentClient.run(context, sessionId,
                        prependSystem(input), allowedTools.stream().map(AiReadTool::name)
                                .collect(java.util.stream.Collectors.toSet()),
                        safeSink, delta -> {
                            streamedText.append(delta);
                            safeSink.emit("delta", Map.of("text", delta));
                        });
                AiChatResponse response = completeExternal(context, sessionId, assistantMessageId, result);
                safeSink.emit("done", response);
                return response;
            }
            if (!"deepseek".equalsIgnoreCase(properties.getProvider())) {
                throw new AiException(AiErrorCode.AI_PROVIDER_001, "不支持的 AI Provider 配置");
            }
            List<AiToolDefinition> definitions = allowedTools.stream()
                    .map(tool -> new AiToolDefinition(tool.name(), tool.description(), tool.parametersSchema()))
                    .toList();
            List<AiToolResult> toolResults = new ArrayList<>();
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            int toolCalls = 0;
            String actualModel = properties.getModel();

            for (int round = 0; round <= Math.max(0, properties.getMaxToolRounds()); round++) {
                safeSink.emit("progress", Map.of("stage", round == 0 ? "analyzing" : "synthesizing",
                        "message", round == 0 ? "正在理解问题" : "正在结合业务事实生成回答"));
                AiModelRoundResult modelResult = modelClient.stream(
                        new AiModelRequest(promptPolicy.instructions(), input, definitions, 2048),
                        delta -> {
                            streamedText.append(delta);
                            safeSink.emit("delta", Map.of("text", delta));
                        });
                totalInputTokens += modelResult.inputTokens();
                totalOutputTokens += modelResult.outputTokens();
                if (!modelResult.modelId().isBlank()) {
                    actualModel = modelResult.modelId();
                }
                if (modelResult.status() != AiModelStatus.Completed) {
                    throw new AiException(AiErrorCode.AI_PROVIDER_001, "模型响应未完整完成");
                }
                if (modelResult.functionCalls().isEmpty()) {
                    AiChatResponse response = complete(context, sessionId, assistantMessageId,
                            modelResult.text(), toolResults, actualModel, totalInputTokens, totalOutputTokens);
                    safeSink.emit("done", response);
                    return response;
                }
                streamedText.setLength(0);
                if (round >= properties.getMaxToolRounds()) {
                    throw new AiException(AiErrorCode.AI_TOOL_001, "AI 工具调用轮次超过服务端上限");
                }
                for (AiFunctionCall call : modelResult.functionCalls()) {
                    toolCalls++;
                    if (toolCalls > properties.getMaxToolCalls()) {
                        throw new AiException(AiErrorCode.AI_TOOL_001, "AI 工具调用次数超过服务端上限");
                    }
                    safeSink.emit("tool_started", Map.of("tool_name", call.name(), "call_id", call.callId()));
                    AiToolResult result = toolExecutor.execute(context, sessionId, call.name(), arguments(call));
                    toolResults.add(result);
                    safeSink.emit("tool_finished", Map.of("tool_name", result.toolName(), "call_id", call.callId(),
                            "status", result.status(), "source_summary", result.sourceSummary()));
                    input.add(Map.of("type", "function_call", "call_id", call.callId(),
                            "name", call.name(), "arguments", call.arguments()));
                    input.add(Map.of("type", "function_call_output", "call_id", call.callId(),
                            "output", json(result)));
                }
            }
            throw new AiException(AiErrorCode.AI_TOOL_001, "AI 工具调用未能收敛");
        } catch (RuntimeException exception) {
            conversationStore.failAssistantMessage(context, assistantMessageId, streamedText.toString(),
                    configuredModel, "Failed");
            throw exception;
        }
    }

    private AiChatResponse completeExternal(AiRequestContext context, UUID sessionId, UUID messageId,
                                            OpenWebUiAgentResult result) {
        if (result.answer().isBlank()) {
            throw new AiException(AiErrorCode.AI_SOURCE_001, "Open WebUI 没有返回可用回答");
        }
        AiChatResponse response = new AiChatResponse(context.requestId(), sessionId, result.answer(),
                result.sourceSummary(), result.timeRangeSummary(), result.toolSummary(), List.of(),
                result.modelId(), result.inputTokens(), result.outputTokens(), "Completed");
        conversationStore.completeAssistantMessage(context, messageId, response);
        return response;
    }

    private List<Map<String, Object>> prependSystem(List<Map<String, Object>> input) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptPolicy.instructions()));
        messages.addAll(input);
        return messages;
    }

    private boolean isOpenWebUiProvider() {
        return "open-webui".equalsIgnoreCase(properties.getProvider())
                || "openwebui".equalsIgnoreCase(properties.getProvider());
    }

    private String configuredModel() {
        return isOpenWebUiProvider() ? properties.getOpenWebuiModel() : properties.getModel();
    }

    private AiChatResponse complete(AiRequestContext context, UUID sessionId, UUID messageId,
                                    String answer, List<AiToolResult> toolResults, String modelId,
                                    int inputTokens, int outputTokens) {
        if ((answer == null || answer.isBlank()) && toolResults.isEmpty()) {
            throw new AiException(AiErrorCode.AI_SOURCE_001, "没有可用于回答的授权事实来源");
        }
        String finalAnswer = answer == null ? "" : answer;
        String sourceSummary = joinDistinct(toolResults.stream().map(AiToolResult::sourceSummary).toList());
        String timeRangeSummary = joinDistinct(toolResults.stream().map(AiToolResult::timeRangeSummary).toList());
        String toolSummary = joinDistinct(toolResults.stream().map(AiToolResult::toolName).toList());
        AiChatResponse response = new AiChatResponse(context.requestId(), sessionId, finalAnswer,
                sourceSummary, timeRangeSummary, toolSummary, List.of(), modelId,
                inputTokens, outputTokens, "Completed");
        conversationStore.completeAssistantMessage(context, messageId, response);
        return response;
    }

    private List<Map<String, Object>> buildInput(List<AiStoredMessage> history, AiChatRequest request) {
        List<Map<String, Object>> input = new ArrayList<>();
        for (AiStoredMessage message : history) {
            input.add(Map.of("role", "Assistant".equals(message.role()) ? "assistant" : "user",
                    "content", message.content()));
        }
        input.add(Map.of("role", "user", "content", userContent(request.message(), request.pageContext())));
        return input;
    }

    private static String userContent(String message, AiPageContext pageContext) {
        if (pageContext == null || pageContext.entityType().isBlank()) {
            return message;
        }
        return message + "\n\n当前页面上下文：entity_type=" + pageContext.entityType()
                + ", entity_id=" + pageContext.entityId() + ", entry_page_code=" + pageContext.entryPageCode();
    }

    private Map<String, Object> arguments(AiFunctionCall call) {
        try {
            Map<String, Object> value = objectMapper.readValue(call.arguments(), new TypeReference<>() { });
            return value == null ? Map.of() : value;
        } catch (Exception exception) {
            throw new AiException(AiErrorCode.AI_INPUT_001, "模型生成了无效的工具参数");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new AiException(AiErrorCode.AI_TOOL_001, "工具结果无法序列化");
        }
    }

    private static String joinDistinct(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                distinct.add(value);
            }
        }
        return String.join("；", distinct);
    }

    private static void validate(AiChatRequest request) {
        if (request == null || request.message().isBlank()) {
            throw new AiException(AiErrorCode.AI_INPUT_001, "message 不能为空");
        }
        if (request.message().length() > MAX_MESSAGE_LENGTH) {
            throw new AiException(AiErrorCode.AI_INPUT_001, "message 不能超过 4000 字符");
        }
        for (String toolName : request.toolWhitelist()) {
            if (toolName == null || toolName.isBlank() || toolName.length() > 128) {
                throw new AiException(AiErrorCode.AI_INPUT_001, "tool_whitelist 包含无效工具名");
            }
        }
    }

    private void validateWhitelist(AiRequestContext context, Set<String> whitelist) {
        for (String toolName : whitelist) {
            AiReadTool tool = toolRegistry.find(toolName)
                    .orElseThrow(() -> new AiException(AiErrorCode.AI_TOOL_002, "工具未注册或未获许可"));
            if (!tool.isAllowed(context)) {
                throw new AiException(AiErrorCode.AI_AUTH_001, "当前用户无指定工具权限");
            }
        }
    }
}
