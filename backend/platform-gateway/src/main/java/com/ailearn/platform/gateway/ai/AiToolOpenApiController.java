package com.ailearn.platform.gateway.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Open WebUI 从 Gateway 读取的白名单业务 API 目录。 */
@RestController
@RequestMapping("/v3/api-docs/ai-tools")
public class AiToolOpenApiController {
    private final AiApiWhitelistCatalog whitelistCatalog;
    private final AiToolOpenApiCatalogService openApiCatalogService;

    public AiToolOpenApiController(AiApiWhitelistCatalog whitelistCatalog,
                                   AiToolOpenApiCatalogService openApiCatalogService) {
        this.whitelistCatalog = whitelistCatalog;
        this.openApiCatalogService = openApiCatalogService;
    }

    /** 返回清单版本、服务目录与全部稳定 operation ID，供部署脚本校验和自动注册。 */
    @GetMapping
    public Map<String, Object> index() {
        AiApiWhitelistCatalog.Catalog catalog = whitelistCatalog.snapshot();
        List<Map<String, Object>> services = catalog.services().values().stream().map(service -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", service.id());
            item.put("name", service.name());
            item.put("catalog_path", service.catalogPath());
            item.put("operation_ids", catalog.operationsFor(service.id()).stream()
                    .map(AiApiWhitelistCatalog.Operation::id).toList());
            return item;
        }).toList();
        return Map.of("version", catalog.version(), "services", services,
                "operation_count", catalog.operations().size());
    }

    /** 返回单个下游服务按唯一白名单过滤后的 OpenAPI。 */
    @GetMapping("/{serviceId}")
    public Mono<ObjectNode> serviceCatalog(@PathVariable String serviceId) {
        return openApiCatalogService.catalog(serviceId);
    }
}
