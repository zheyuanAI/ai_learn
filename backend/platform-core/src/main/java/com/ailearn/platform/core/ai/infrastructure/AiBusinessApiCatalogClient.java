package com.ailearn.platform.core.ai.infrastructure;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 从 Gateway 读取唯一白名单生成的业务 API operation 目录，供能力页面展示。 */
@Component
public class AiBusinessApiCatalogClient {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiBusinessApiCatalogClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    /** 目录暂不可用时返回空集合，不影响 AI 页面加载；实际工具调用仍由 Gateway 强制校验。 */
    public List<String> operationIds() {
        try {
            String baseUrl = properties.getGatewayBaseUrl().toString().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v3/api-docs/ai-tools"))
                    .timeout(properties.getConnectTimeout()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<String> result = new ArrayList<>();
            for (JsonNode service : root.path("services")) {
                for (JsonNode operationId : service.path("operation_ids")) {
                    if (!operationId.asText("").isBlank()) {
                        result.add(operationId.asText());
                    }
                }
            }
            return result.stream().distinct().sorted().toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
