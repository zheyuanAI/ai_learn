package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.application.OpenWebUiInvocationContextStore;
import com.ailearn.platform.core.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Open WebUI 协议负载只启用受控 Knowledge 与管理员注册的 WMS Tool Server。 */
class OpenWebUiAgentClientTest {

    @Test
    void shouldUseHttp11ForUvicornCompatibility() {
        AiProperties properties = new AiProperties();
        OpenWebUiAgentClient client = new OpenWebUiAgentClient(properties, new ObjectMapper(),
                mock(OpenWebUiInvocationContextStore.class));

        assertEquals(HttpClient.Version.HTTP_1_1, client.createHttpClient().version());
    }

    @Test
    void shouldBuildNativeAgentPayloadWithoutArbitraryToolsOrHighRiskFeatures() {
        AiProperties properties = new AiProperties();
        properties.setOpenWebuiModel("wms-assistant");
        properties.setOpenWebuiToolServerId("server:wms");
        OpenWebUiAgentClient client = new OpenWebUiAgentClient(properties, new ObjectMapper(),
                mock(OpenWebUiInvocationContextStore.class));

        Map<String, Object> payload = client.completionPayload("chat-1", "message-1",
                List.of(Map.of("role", "user", "content", "为什么未完成？")), true);

        assertEquals("wms-assistant", payload.get("model"));
        assertEquals(List.of("server:wms"), payload.get("tool_ids"));
        assertTrue(payload.containsKey("session_id"));
        assertFalse(payload.containsKey("tools"));
        @SuppressWarnings("unchecked")
        Map<String, Boolean> features = (Map<String, Boolean>) payload.get("features");
        assertEquals(Map.of("web_search", false, "code_interpreter", false,
                "image_generation", false, "memory", false), features);
    }

    @Test
    void shouldBuildCompleteChatMessageTree() {
        AiProperties properties = new AiProperties();
        properties.setOpenWebuiModel("wms-assistant");
        OpenWebUiAgentClient client = new OpenWebUiAgentClient(properties, new ObjectMapper(),
                mock(OpenWebUiInvocationContextStore.class));

        Map<String, Object> payload = client.createChatPayload("user-1", "assistant-1", "问题");

        @SuppressWarnings("unchecked")
        Map<String, Object> chat = (Map<String, Object>) payload.get("chat");
        @SuppressWarnings("unchecked")
        Map<String, Object> history = (Map<String, Object>) chat.get("history");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> messages =
                (Map<String, Map<String, Object>>) history.get("messages");
        assertEquals("assistant-1", history.get("currentId"));
        assertEquals(List.of("assistant-1"), messages.get("user-1").get("childrenIds"));
        assertEquals("user-1", messages.get("assistant-1").get("parentId"));
    }

    @Test
    void shouldAggregateKnowledgeAndExecutedToolMetadata() throws Exception {
        AiProperties properties = new AiProperties();
        OpenWebUiAgentClient client = new OpenWebUiAgentClient(properties, new ObjectMapper(),
                mock(OpenWebUiInvocationContextStore.class));
        var message = new ObjectMapper().readTree("{\"sources\":[{},{}]}");
        var observations = List.of(
                new OpenWebUiInvocationContextStore.ToolObservation(
                        "querySalesOrderStatus", "sales order facts", "数据更新至当前", "Success"),
                new OpenWebUiInvocationContextStore.ToolObservation(
                        "querySalesOrderStatus", "sales order facts", "数据更新至当前", "Success"));

        assertEquals("Open WebUI 返回 2 个受控知识来源；sales order facts",
                client.sourceSummary(message, observations));
        assertEquals("数据更新至当前", client.timeRangeSummary(observations));
        assertEquals("querySalesOrderStatus", client.toolSummary(observations));
    }

    @Test
    void shouldEmitOpenWebUiToolLifecycleEventsInOrder() {
        AiProperties properties = new AiProperties();
        OpenWebUiAgentClient client = new OpenWebUiAgentClient(properties, new ObjectMapper(),
                mock(OpenWebUiInvocationContextStore.class));
        List<String> events = new ArrayList<>();
        var observations = List.of(
                new OpenWebUiInvocationContextStore.ToolObservation(
                        "queryTrace", "", "", "Running", "call-1"),
                new OpenWebUiInvocationContextStore.ToolObservation(
                        "queryTrace", "trace facts", "today", "Success", "call-1"));

        int emitted = client.emitNewToolObservations(observations, 0,
                (name, data) -> events.add(name + ":" + ((Map<?, ?>) data).get("call_id")));

        assertEquals(2, emitted);
        assertEquals(List.of("tool_started:call-1", "tool_finished:call-1"), events);
        assertEquals(2, client.emitNewToolObservations(observations, emitted,
                (name, data) -> events.add(name)));
    }
}
