package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.exception.AiException;
import com.ailearn.platform.core.ai.model.AiModelRequest;
import com.ailearn.platform.core.ai.model.AiToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DeepSeek Provider 请求协议与关闭状态回归，不访问真实网络。 */
class DeepSeekResponsesClientTest {

    @Test
    void shouldSerializeResponsesRequestWithoutSecret() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setModel("deepseek-v4-flash");
        properties.setApiKey("never-serialize-this-key");
        ObjectMapper mapper = new ObjectMapper();
        DeepSeekResponsesClient client = new DeepSeekResponsesClient(properties, mapper);
        AiModelRequest request = new AiModelRequest("只读回答", List.of(
                Map.of("role", "user", "content", "查询订单")), List.of(
                new AiToolDefinition("queryTrace", "查询追溯", Map.of(
                        "type", "object", "properties", Map.of()))), 1024);

        String json = client.requestJson(request);
        JsonNode root = mapper.readTree(json);

        assertEquals("deepseek-v4-flash", root.path("model").asText());
        assertTrue(root.path("stream").asBoolean());
        assertEquals("function", root.path("tools").get(0).path("type").asText());
        assertEquals("queryTrace", root.path("tools").get(0).path("name").asText());
        assertFalse(json.contains("never-serialize-this-key"));
    }

    @Test
    void shouldNormalizeBaseUrlAndRejectDisabledProviderBeforeNetwork() {
        AiProperties properties = new AiProperties();
        properties.setBaseUrl(URI.create("https://api.deepseek.com///"));
        DeepSeekResponsesClient client = new DeepSeekResponsesClient(properties, new ObjectMapper());

        assertEquals("https://api.deepseek.com/responses", client.responsesUri().toString());
        assertThrows(AiException.class, () -> client.stream(
                new AiModelRequest("", List.of(), List.of(), 100), ignored -> { }));
    }
}
