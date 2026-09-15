package com.ailearn.platform.gateway.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 唯一白名单必须精确匹配 method + path，并在重复或宽泛配置时快速失败。 */
class AiApiWhitelistCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldLoadFirstBatchRepositoryWhitelist() {
        Path file = Path.of("..", "..", "deploy", "openwebui", "wms-ai-api-whitelist.yml")
                .toAbsolutePath().normalize();
        AiApiWhitelistCatalog catalog = new AiApiWhitelistCatalog(properties(file));

        catalog.load();

        assertEquals(36, catalog.snapshot().operations().size());
        assertEquals(30, catalog.snapshot().operationsFor("core").size());
        assertEquals(6, catalog.snapshot().operationsFor("iot").size());
        assertTrue(catalog.snapshot().operations().stream()
                .allMatch(operation -> "GET".equals(operation.method())));
    }

    @Test
    void shouldLoadAndMatchOnlyRegisteredMethodAndPath() throws Exception {
        Path file = write("""
                version: 1
                services:
                  core:
                    openapi-url: http://127.0.0.1:10003/v3/api-docs
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
        GatewayAiProperties properties = properties(file);
        AiApiWhitelistCatalog catalog = new AiApiWhitelistCatalog(properties);

        catalog.load();

        assertEquals(1, catalog.snapshot().operations().size());
        assertTrue(catalog.find("GET", "/api/sales-orders/5b989f30-86b2-4204-83e3-c50bf80f152d").isPresent());
        assertTrue(catalog.find("POST", "/api/sales-orders/5b989f30-86b2-4204-83e3-c50bf80f152d").isEmpty());
        assertTrue(catalog.find("GET", "/api/sales-orders/1/approve").isEmpty());
    }

    @Test
    void shouldRejectDuplicateRoute() throws Exception {
        Path file = write("""
                version: 1
                services:
                  core:
                    openapi-url: http://127.0.0.1:10003/v3/api-docs
                    catalog-path: /v3/api-docs/ai-tools/core
                    name: Core
                groups:
                  sales:
                    description: 销售查询
                    operations:
                      - id: first
                        service: core
                        method: GET
                        path: /api/sales-orders
                        summary: 第一个
                      - id: second
                        service: core
                        method: GET
                        path: /api/sales-orders
                        summary: 第二个
                """);
        AiApiWhitelistCatalog catalog = new AiApiWhitelistCatalog(properties(file));

        IllegalStateException exception = assertThrows(IllegalStateException.class, catalog::load);

        assertTrue(exception.getMessage().contains("method + path 重复"));
    }

    @Test
    void shouldRejectWildcardBusinessPath() throws Exception {
        Path file = write("""
                version: 1
                services:
                  core:
                    openapi-url: http://127.0.0.1:10003/v3/api-docs
                    catalog-path: /v3/api-docs/ai-tools/core
                    name: Core
                groups:
                  sales:
                    description: 销售查询
                    operations:
                      - id: broadSearch
                        service: core
                        method: GET
                        path: /api/sales-orders/**
                        summary: 宽泛路径
                """);
        AiApiWhitelistCatalog catalog = new AiApiWhitelistCatalog(properties(file));

        assertThrows(IllegalStateException.class, catalog::load);
    }

    private GatewayAiProperties properties(Path file) {
        GatewayAiProperties properties = new GatewayAiProperties();
        properties.setEnabled(true);
        properties.setApiWhitelistPath(file.toString());
        return properties;
    }

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("whitelist.yml");
        Files.writeString(file, content);
        return file;
    }
}
