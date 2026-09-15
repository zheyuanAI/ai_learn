package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiStreamSink;
import com.ailearn.platform.core.ai.application.OpenWebUiInvocationContextStore;
import com.ailearn.platform.core.ai.application.OpenWebUiToolAuditRecorder;
import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * Open WebUI 服务端 Agent API 适配器。
 * 采用保存短期聊天的原生多轮工具路径，结束后删除 Open WebUI 聊天，WMS 数据库仍是会话与审计事实源。
 */
@Component
public class OpenWebUiAgentClient {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(400);
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenWebUiInvocationContextStore invocationContextStore;
    private final OpenWebUiToolAuditRecorder toolAuditRecorder;

    /** 注入 Open WebUI 配置、JSON 映射器和工具回调授权映射。 */
    @Autowired
    public OpenWebUiAgentClient(AiProperties properties, ObjectMapper objectMapper,
                                OpenWebUiInvocationContextStore invocationContextStore,
                                OpenWebUiToolAuditRecorder toolAuditRecorder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.invocationContextStore = invocationContextStore;
        this.toolAuditRecorder = toolAuditRecorder;
    }

    /** 兼容协议单元测试；生产路径始终注入审计记录器。 */
    public OpenWebUiAgentClient(AiProperties properties, ObjectMapper objectMapper,
                                OpenWebUiInvocationContextStore invocationContextStore) {
        this(properties, objectMapper, invocationContextStore, null);
    }

    /**
     * 用途：让 Open WebUI 使用受控知识与 WMS 查询工具完成一次多轮分析。
     * 入参：可信用户、本地会话、完整模型消息、允许工具及文本增量出口；出参：最终回答与来源摘要。
     */
    public OpenWebUiAgentResult run(AiRequestContext context, UUID localSessionId,
                                    List<Map<String, Object>> messages, Set<String> allowedTools,
                                    AiStreamSink sink, Consumer<String> textDeltaConsumer) {
        requireConfigured();
        String userMessageId = UUID.randomUUID().toString();
        String assistantMessageId = UUID.randomUUID().toString();
        String chatId = null;
        boolean observationsAudited = false;
        try {
            JsonNode created = sendJson("POST", "/api/v1/chats/new",
                    createChatPayload(userMessageId, assistantMessageId, lastUserText(messages)));
            chatId = created.path("id").asText("");
            if (!StringUtils.hasText(chatId)) {
                throw providerError("Open WebUI 未返回聊天标识");
            }
            invocationContextStore.bind(chatId, assistantMessageId, localSessionId, context, allowedTools);
            sink.emit("progress", Map.of("stage", "agent_started", "message", "正在查询业务知识与授权实时数据"));
            sendJson("POST", "/api/chat/completions",
                    completionPayload(chatId, assistantMessageId, messages, true));

            String answer = waitForAnswer(chatId, assistantMessageId, sink, textDeltaConsumer);
            if (!StringUtils.hasText(answer)) {
                throw providerError("Open WebUI 未生成有效回答");
            }
            JsonNode message = assistantMessage(chat(chatId), assistantMessageId);
            JsonNode usage = message.path("usage");
            List<OpenWebUiInvocationContextStore.ToolObservation> observations =
                    invocationContextStore.results(chatId, assistantMessageId);
            if (toolAuditRecorder != null) {
                observationsAudited = true;
                toolAuditRecorder.record(context, localSessionId, observations);
            }
            return new OpenWebUiAgentResult(answer, sourceSummary(message, observations),
                    timeRangeSummary(observations), toolSummary(observations),
                    properties.getOpenWebuiModel(), usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0));
        } finally {
            if (chatId != null) {
                if (toolAuditRecorder != null && !observationsAudited) {
                    try {
                        toolAuditRecorder.record(context, localSessionId,
                                invocationContextStore.results(chatId, assistantMessageId));
                    } catch (RuntimeException ignored) {
                        // 修改用途：Provider 已失败时保留原始故障，审计基础设施异常不能覆盖首要错误。
                    }
                }
                invocationContextStore.remove(chatId, assistantMessageId);
                deleteChatQuietly(chatId);
            }
        }
    }

    /** 包内可见，供协议测试确认短期聊天的消息树字段。 */
    Map<String, Object> createChatPayload(String userMessageId, String assistantMessageId, String prompt) {
        long timestamp = Instant.now().getEpochSecond();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userMessageId);
        user.put("role", "user");
        user.put("content", prompt);
        user.put("timestamp", timestamp);
        user.put("models", List.of(properties.getOpenWebuiModel()));
        user.put("childrenIds", List.of(assistantMessageId));
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("id", assistantMessageId);
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put("parentId", userMessageId);
        assistant.put("childrenIds", List.of());
        assistant.put("model", properties.getOpenWebuiModel());
        assistant.put("modelName", properties.getOpenWebuiModel());
        assistant.put("modelIdx", 0);
        assistant.put("done", false);
        assistant.put("timestamp", timestamp + 1);
        Map<String, Object> history = Map.of("currentId", assistantMessageId,
                "messages", Map.of(userMessageId, user, assistantMessageId, assistant));
        return Map.of("chat", Map.of("title", "WMS AI 临时问答",
                "models", List.of(properties.getOpenWebuiModel()), "history", history));
    }

    /** 包内可见，供协议测试确保关闭高风险内置能力且不传入任意 tools 定义。 */
    Map<String, Object> completionPayload(String chatId, String assistantMessageId,
                                          List<Map<String, Object>> messages, boolean includeWmsTools) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getOpenWebuiModel());
        payload.put("messages", messages);
        payload.put("stream", true);
        payload.put("chat_id", chatId);
        payload.put("id", assistantMessageId);
        // session_id 启用 Open WebUI 原生多轮 Agent 与模型预设绑定的 Knowledge；高风险内置能力在 features 中显式关闭。
        payload.put("session_id", "wms-" + UUID.randomUUID());
        payload.put("features", Map.of("web_search", false, "code_interpreter", false,
                "image_generation", false, "memory", false));
        payload.put("background_tasks", Map.of("title_generation", false,
                "tags_generation", false, "follow_up_generation", false));
        if (includeWmsTools) {
            payload.put("tool_ids", properties.getOpenWebuiToolServerIds().stream().sorted().toList());
        }
        return payload;
    }

    private String waitForAnswer(String chatId, String assistantMessageId, AiStreamSink sink,
                                 Consumer<String> textDeltaConsumer) {
        long timeoutMillis = Math.max(1000L, properties.getStreamTimeout().toMillis());
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String emitted = "";
        int emittedObservations = 0;
        while (System.currentTimeMillis() < deadline) {
            JsonNode chat = chat(chatId);
            String current = assistantMessage(chat, assistantMessageId).path("content").asText("");
            if (current.startsWith(emitted) && current.length() > emitted.length()) {
                String delta = current.substring(emitted.length());
                textDeltaConsumer.accept(delta);
                emitted = current;
            }
            emittedObservations = emitNewToolObservations(
                    invocationContextStore.results(chatId, assistantMessageId), emittedObservations, sink);
            JsonNode tasks = sendJson("GET", "/api/tasks/chat/" + encodePath(chatId), null);
            if (!tasks.path("task_ids").isArray() || tasks.path("task_ids").isEmpty()) {
                JsonNode finalChat = chat(chatId);
                String answer = assistantMessage(finalChat, assistantMessageId).path("content").asText("");
                if (answer.startsWith(emitted) && answer.length() > emitted.length()) {
                    textDeltaConsumer.accept(answer.substring(emitted.length()));
                }
                emitNewToolObservations(
                        invocationContextStore.results(chatId, assistantMessageId), emittedObservations, sink);
                return answer;
            }
            pause();
        }
        throw providerError("Open WebUI Agent 执行超时");
    }

    /**
     * 用途：把 Open WebUI 后台工具回调产生的非敏感观察结果转换为浏览器 SSE 事件。
     * 入参：当前观察列表、已发送数量和事件出口；出参：新的已发送数量；流程：按 Redis 顺序发送开始或结束事件。
     */
    int emitNewToolObservations(List<OpenWebUiInvocationContextStore.ToolObservation> observations,
                                int emittedCount, AiStreamSink sink) {
        List<OpenWebUiInvocationContextStore.ToolObservation> safe = safeObservations(observations);
        int startIndex = Math.max(0, Math.min(emittedCount, safe.size()));
        for (int index = startIndex; index < safe.size(); index++) {
            OpenWebUiInvocationContextStore.ToolObservation observation = safe.get(index);
            String callId = StringUtils.hasText(observation.callId())
                    ? observation.callId() : "openwebui-tool-" + index;
            if ("Running".equalsIgnoreCase(observation.status())) {
                sink.emit("tool_started", Map.of("tool_name", observation.toolName(), "call_id", callId));
            } else {
                sink.emit("tool_finished", Map.of("tool_name", observation.toolName(), "call_id", callId,
                        "status", observation.status(), "source_summary", observation.sourceSummary()));
            }
        }
        return safe.size();
    }

    private JsonNode chat(String chatId) {
        return sendJson("GET", "/api/v1/chats/" + encodePath(chatId), null);
    }

    private static JsonNode assistantMessage(JsonNode chat, String assistantMessageId) {
        JsonNode message = chat.path("chat").path("history").path("messages").path(assistantMessageId);
        if (message.isMissingNode() || message.isNull()) {
            throw providerError("Open WebUI 回答消息不存在");
        }
        return message;
    }

    /** 汇总 Open WebUI Knowledge 与已成功 WMS 工具的来源，不读取或缓存工具业务正文。 */
    String sourceSummary(JsonNode message,
                         List<OpenWebUiInvocationContextStore.ToolObservation> observations) {
        List<String> values = new ArrayList<>();
        JsonNode sources = message.path("sources");
        if (sources.isArray() && !sources.isEmpty()) {
            values.add("Open WebUI 返回 " + sources.size() + " 个受控知识来源");
        } else {
            values.add("Open WebUI WMS 助手模型预设");
        }
        completedObservations(observations).stream().map(OpenWebUiInvocationContextStore.ToolObservation::sourceSummary)
                .filter(StringUtils::hasText).forEach(values::add);
        return joinDistinct(values);
    }

    /** 汇总 WMS 工具明确返回的事实时间范围。 */
    String timeRangeSummary(List<OpenWebUiInvocationContextStore.ToolObservation> observations) {
        return joinDistinct(completedObservations(observations).stream()
                .map(OpenWebUiInvocationContextStore.ToolObservation::timeRangeSummary).toList());
    }

    /** 汇总当前问答实际成功执行的 WMS 工具。 */
    String toolSummary(List<OpenWebUiInvocationContextStore.ToolObservation> observations) {
        return joinDistinct(completedObservations(observations).stream()
                .map(OpenWebUiInvocationContextStore.ToolObservation::toolName).toList());
    }

    private JsonNode sendJson(String method, String path, Object payload) {
        HttpClient client = createHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .timeout(properties.getStreamTimeout())
                .header("Authorization", "Bearer " + properties.getOpenWebuiApiKey())
                .header("Accept", "application/json");
        if (payload != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json(payload)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerError("Open WebUI 返回 HTTP " + response.statusCode());
            }
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerError("Open WebUI 请求已中断");
        } catch (java.io.IOException exception) {
            throw providerError("Open WebUI 连接或响应读取失败");
        }
    }

    /**
     * 用途：创建兼容本机 Uvicorn 的 Open WebUI HTTP 客户端。
     * 出参：固定使用 HTTP/1.1 的客户端；流程：避免 JDK 默认 h2c 升级被 Uvicorn 作为无效请求拒绝。
     */
    HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    private void deleteChatQuietly(String chatId) {
        try {
            sendJson("DELETE", "/api/v1/chats/" + encodePath(chatId), null);
        } catch (RuntimeException ignored) {
            // 临时聊天清理失败不能覆盖主流程结果；部署监控需继续告警并由 Open WebUI 保留策略兜底。
        }
    }

    private URI uri(String path) {
        String base = properties.getOpenWebuiBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + path);
    }

    private static String encodePath(String value) {
        if (!StringUtils.hasText(value) || !value.matches("[A-Za-z0-9_:-]{1,128}")) {
            throw providerError("Open WebUI 返回了无效关联标识");
        }
        return value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AiException(AiErrorCode.AI_INPUT_001, "Open WebUI 请求无法序列化");
        }
    }

    private void requireConfigured() {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getOpenWebuiApiKey())) {
            throw providerError("Open WebUI 未启用或未配置 API Key");
        }
        if (properties.getOpenWebuiBaseUrl() == null
                || !StringUtils.hasText(properties.getOpenWebuiModel())) {
            throw providerError("Open WebUI 地址或模型预设未配置");
        }
        if (!StringUtils.hasText(properties.getToolServiceSecret())) {
            throw providerError("WMS AI 工具服务密钥未配置");
        }
        if (properties.getOpenWebuiToolServerIds().isEmpty()
                || properties.getOpenWebuiToolServerIds().stream()
                .anyMatch(id -> !StringUtils.hasText(id) || !id.matches("server:[a-z0-9_]+"))) {
            throw providerError("Open WebUI WMS Tool Server ID 集合配置无效");
        }
    }

    private static String lastUserText(List<Map<String, Object>> messages) {
        List<Map<String, Object>> safeMessages = messages == null ? new ArrayList<>() : messages;
        for (int index = safeMessages.size() - 1; index >= 0; index--) {
            Map<String, Object> message = safeMessages.get(index);
            if ("user".equals(message.get("role"))) {
                return String.valueOf(message.getOrDefault("content", ""));
            }
        }
        return "WMS AI 问答";
    }

    private static List<OpenWebUiInvocationContextStore.ToolObservation> safeObservations(
            List<OpenWebUiInvocationContextStore.ToolObservation> observations) {
        return observations == null ? List.of() : observations;
    }

    private static List<OpenWebUiInvocationContextStore.ToolObservation> completedObservations(
            List<OpenWebUiInvocationContextStore.ToolObservation> observations) {
        return safeObservations(observations).stream()
                .filter(value -> "Success".equalsIgnoreCase(value.status()))
                .toList();
    }

    private static String joinDistinct(List<String> values) {
        Set<String> distinct = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                distinct.add(value);
            }
        }
        return String.join("；", distinct);
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerError("Open WebUI 等待过程已中断");
        }
    }

    private static AiException providerError(String message) {
        return new AiException(AiErrorCode.AI_PROVIDER_001, message);
    }
}
