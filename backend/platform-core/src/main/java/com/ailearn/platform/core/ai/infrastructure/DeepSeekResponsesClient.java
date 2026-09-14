package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import com.ailearn.platform.core.ai.model.AiModelClient;
import com.ailearn.platform.core.ai.model.AiModelRequest;
import com.ailearn.platform.core.ai.model.AiModelRoundResult;
import com.ailearn.platform.core.ai.model.AiModelStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** DeepSeek 官方 Responses API 的模型 Provider 适配器。 */
@Component
public class DeepSeekResponsesClient implements AiModelClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    /** 注入服务端 Provider 配置和项目统一 JSON 映射器。 */
    public DeepSeekResponsesClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiModelRoundResult stream(AiModelRequest request, Consumer<String> textDeltaConsumer) {
        requireEnabled();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder(responsesUri())
                .timeout(properties.getStreamTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson(request)))
                .build();
        try {
            HttpResponse<java.util.stream.Stream<String>> response = client.send(
                    httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new AiException(AiErrorCode.AI_PROVIDER_001,
                        "DeepSeek 返回 HTTP " + response.statusCode());
            }
            DeepSeekSseParser parser = new DeepSeekSseParser(objectMapper, textDeltaConsumer);
            try (java.util.stream.Stream<String> lines = response.body()) {
                lines.forEach(parser::acceptLine);
            }
            AiModelRoundResult result = parser.finish();
            if (result.status() == AiModelStatus.Failed) {
                throw new AiException(AiErrorCode.AI_PROVIDER_001,
                        result.errorMessage().isBlank() ? "DeepSeek 流式响应失败" : result.errorMessage());
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorCode.AI_PROVIDER_001, "DeepSeek 请求已中断");
        } catch (java.io.IOException exception) {
            throw new AiException(AiErrorCode.AI_PROVIDER_001, "DeepSeek 连接或流式读取失败");
        }
    }

    /** 包内可见以便验证请求协议，不包含 Authorization 密钥。 */
    String requestJson(AiModelRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("instructions", request.instructions());
        payload.put("input", request.input());
        payload.put("stream", true);
        payload.put("max_output_tokens", request.maxOutputTokens());
        if (!request.tools().isEmpty()) {
            List<Map<String, Object>> tools = request.tools().stream().map(tool -> Map.<String, Object>of(
                    "type", "function",
                    "name", tool.name(),
                    "description", tool.description(),
                    "parameters", tool.parameters()
            )).toList();
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AiException(AiErrorCode.AI_INPUT_001, "模型请求无法序列化");
        }
    }

    /** 包内可见以便验证 Base URL 规范化。 */
    URI responsesUri() {
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + "/responses");
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiException(AiErrorCode.AI_PROVIDER_001, "DeepSeek 未启用或未配置 API Key");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new AiException(AiErrorCode.AI_PROVIDER_001, "DeepSeek 模型编号未配置");
        }
    }
}
