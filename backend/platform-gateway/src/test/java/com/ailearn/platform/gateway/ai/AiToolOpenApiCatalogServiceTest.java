package com.ailearn.platform.gateway.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gateway 发布给 Open WebUI 的 OpenAPI 必须只包含唯一 YAML 登记的业务操作。 */
class AiToolOpenApiCatalogServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldPublishOnlyWhitelistedOperationAndRewriteServer() throws Exception {
        Path file = tempDir.resolve("whitelist.yml");
        Files.writeString(file, """
                version: 1
                services:
                  core:
                    openapi-url: http://core/v3/api-docs
                    catalog-path: /v3/api-docs/ai-tools/core
                    name: Core
                groups:
                  sales:
                    description: 销售查询
                    operations:
                      - id: salesOrderDetail
                        service: core
                        method: GET
                        path: /api/sales-orders/{id}
                        summary: 查询销售订单详情
                """);
        GatewayAiProperties properties = new GatewayAiProperties();
        properties.setEnabled(true);
        properties.setApiWhitelistPath(file.toString());
        properties.setPublicBaseUrl("http://127.0.0.1:20001/");
        AiApiWhitelistCatalog catalog = new AiApiWhitelistCatalog(properties);
        catalog.load();
        String source = """
                {"openapi":"3.0.1","paths":{
                  "/api/sales-orders/{id}":{"get":{"operationId":"detail","responses":{"200":{"description":"OK"}}}},
                  "/api/sales-orders/{id}/approve":{"post":{"operationId":"approve","responses":{"200":{"description":"OK"}}}}
                },"components":{"schemas":{"Order":{"type":"object"}}}}
                """;
        WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(source).build()));
        AiToolOpenApiCatalogService service = new AiToolOpenApiCatalogService(catalog, properties,
                new ObjectMapper(), webClient);

        StepVerifier.create(service.catalog("core"))
                .assertNext(result -> assertCatalog(result))
                .verifyComplete();
    }

    private static void assertCatalog(ObjectNode result) {
        assertEquals("http://127.0.0.1:20001", result.path("servers").get(0).path("url").asText());
        assertEquals("salesOrderDetail", result.path("paths").path("/api/sales-orders/{id}")
                .path("get").path("operationId").asText());
        assertTrue(result.path("components").path("schemas").has("Order"));
        assertFalse(result.path("paths").has("/api/sales-orders/{id}/approve"));
    }
}
