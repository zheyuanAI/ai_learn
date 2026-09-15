package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.dto.AiChatRequest;
import com.ailearn.platform.core.ai.dto.AiChatResponse;
import com.ailearn.platform.core.ai.model.AiFunctionCall;
import com.ailearn.platform.core.ai.infrastructure.OpenWebUiAgentClient;
import com.ailearn.platform.core.ai.infrastructure.OpenWebUiAgentResult;
import com.ailearn.platform.core.ai.model.AiModelClient;
import com.ailearn.platform.core.ai.model.AiModelRequest;
import com.ailearn.platform.core.ai.model.AiModelRoundResult;
import com.ailearn.platform.core.ai.model.AiModelStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** AI 聊天编排器以 Fake Provider 验证工具循环、事件和最终消息收口。 */
class AiChatOrchestratorTest {

    @Test
    void shouldDelegateAgentLoopToOpenWebUiWithoutRenderingNavigationActions() {
        UUID sessionId = UUID.randomUUID();
        AiProperties properties = new AiProperties();
        properties.setProvider("open-webui");
        properties.setOpenWebuiModel("wms-assistant");
        AiReadTool traceTool = new FakeTraceTool();
        AiToolRegistry registry = new AiToolRegistry(List.of(traceTool));
        AiToolExecutor executor = mock(AiToolExecutor.class);
        FakeConversationStore store = new FakeConversationStore(sessionId);
        OpenWebUiAgentClient openWebUi = mock(OpenWebUiAgentClient.class);
        when(openWebUi.run(any(), eq(sessionId), any(), eq(Set.of()), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Consumer<String> delta = invocation.getArgument(5);
                    delta.accept("卡在拣货环节");
                    return new OpenWebUiAgentResult("卡在拣货环节", "WMS 追溯事实", "更新至当前",
                            "queryTrace", "wms-assistant", 12, 8);
                });
        AiChatOrchestrator orchestrator = new AiChatOrchestrator(properties, mock(AiModelClient.class),
                registry, executor, store, new AiPromptPolicy(), new ObjectMapper(), openWebUi);
        AiRequestContext context = new AiRequestContext(
                UUID.randomUUID(), UUID.randomUUID(), "jti", Set.of("trace:chain:view"),
                ZoneId.of("Asia/Shanghai"), "fingerprint", "request-openwebui");
        List<String> events = new ArrayList<>();

        AiChatResponse response = orchestrator.chat(context,
                new AiChatRequest("为什么未完成？", null, Set.of(), null),
                (event, data) -> events.add(event));

        assertEquals("卡在拣货环节", response.answer());
        assertEquals("wms-assistant", response.modelId());
        assertTrue(response.navigationActions().isEmpty());
        assertEquals(List.of("meta", "delta", "done"), events);
        verify(openWebUi).run(any(), eq(sessionId), any(), eq(Set.of()), any(), any());
    }

    @Test
    void shouldExecuteAuthorizedFunctionCallAndEmitFinalDelta() {
        UUID sessionId = UUID.randomUUID();
        AiProperties properties = new AiProperties();
        properties.setProvider("deepseek");
        properties.setModel("deepseek-v4-flash");
        properties.setMaxToolRounds(4);
        properties.setMaxToolCalls(8);
        AiReadTool traceTool = new FakeTraceTool();
        AiToolRegistry registry = new AiToolRegistry(List.of(traceTool));
        AiToolExecutor executor = mock(AiToolExecutor.class);
        when(executor.execute(any(), eq(sessionId), eq("queryTrace"), any())).thenReturn(
                new AiToolResult("queryTrace", Map.of("status", "Approved"),
                        "销售与追溯事实", "数据更新至 2026-09-12", List.of(), AiToolStatus.Success));
        FakeConversationStore store = new FakeConversationStore(sessionId);
        FakeModelClient model = new FakeModelClient(List.of(
                new AiModelRoundResult("resp-1", "deepseek-v4-flash", "", List.of(
                        new AiFunctionCall("call-1", "queryTrace",
                                "{\"entity_type\":\"SALES_ORDER\",\"entity_id\":\"00000000-0000-0000-0000-000000000001\"}")),
                        10, 2, AiModelStatus.Completed, ""),
                new AiModelRoundResult("resp-2", "deepseek-v4-flash", "订单已审核。", List.of(),
                        20, 6, AiModelStatus.Completed, "")
        ));
        AiChatOrchestrator orchestrator = new AiChatOrchestrator(properties, model, registry, executor,
                store, new AiPromptPolicy(), new ObjectMapper());
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("trace:chain:view"), ZoneId.of("Asia/Shanghai"), "fp", "req-1");
        List<String> events = new ArrayList<>();

        AiChatResponse response = orchestrator.chat(context,
                new AiChatRequest("查询订单", null, Set.of("queryTrace"), null),
                (name, data) -> events.add(name));

        assertEquals("订单已审核。", response.answer());
        assertEquals(30, response.inputTokens());
        assertEquals(8, response.outputTokens());
        assertEquals(List.of("meta", "progress", "tool_started", "tool_finished",
                "progress", "delta", "done"), events);
        assertTrue(model.requests.get(1).input().stream()
                .anyMatch(item -> "function_call_output".equals(item.get("type"))));
        assertEquals("Completed", store.completed.status());
    }

    private static final class FakeModelClient implements AiModelClient {
        private final Deque<AiModelRoundResult> results;
        private final List<AiModelRequest> requests = new ArrayList<>();

        private FakeModelClient(List<AiModelRoundResult> results) {
            this.results = new ArrayDeque<>(results);
        }

        @Override
        public AiModelRoundResult stream(AiModelRequest request, Consumer<String> textDeltaConsumer) {
            requests.add(request);
            AiModelRoundResult result = results.removeFirst();
            if (result.functionCalls().isEmpty() && !result.text().isEmpty()) {
                textDeltaConsumer.accept(result.text());
            }
            return result;
        }
    }

    private static final class FakeTraceTool implements AiReadTool {
        @Override
        public String name() {
            return "queryTrace";
        }

        @Override
        public Set<String> anyOfPermissions() {
            return Set.of("trace:chain:view");
        }

        @Override
        public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
            throw new UnsupportedOperationException("测试由 mock executor 执行");
        }
    }

    private static final class FakeConversationStore implements AiConversationStore {
        private final UUID sessionId;
        private AiChatResponse completed;

        private FakeConversationStore(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public UUID openSession(AiRequestContext context, UUID requestedSessionId) {
            return sessionId;
        }

        @Override
        public List<AiStoredMessage> recentMessages(AiRequestContext context, UUID sessionId, int limit) {
            return List.of();
        }

        @Override
        public void saveUserMessage(AiRequestContext context, UUID sessionId, String content) {
        }

        @Override
        public UUID startAssistantMessage(AiRequestContext context, UUID sessionId, String modelId) {
            return UUID.randomUUID();
        }

        @Override
        public void completeAssistantMessage(AiRequestContext context, UUID messageId, AiChatResponse response) {
            completed = response;
        }

        @Override
        public void failAssistantMessage(AiRequestContext context, UUID messageId, String partialContent,
                                         String modelId, String status) {
        }
    }
}
