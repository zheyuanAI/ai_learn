package com.ailearn.platform.gateway.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** 根据唯一白名单从下游完整 OpenAPI 生成 Open WebUI 可读取的业务工具目录。 */
@Service
public class AiToolOpenApiCatalogService {
    private final AiApiWhitelistCatalog whitelistCatalog;
    private final GatewayAiProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public AiToolOpenApiCatalogService(AiApiWhitelistCatalog whitelistCatalog,
                                       GatewayAiProperties properties,
                                       ObjectMapper objectMapper,
                                       WebClient.Builder webClientBuilder) {
        this.whitelistCatalog = whitelistCatalog;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    /**
     * 用途：生成一个服务的过滤 OpenAPI。
     * 入参：白名单服务编码；出参：只含登记操作的 OpenAPI；流程：拉取原文档、逐操作复制并重写 operationId。
     */
    public Mono<ObjectNode> catalog(String serviceId) {
        AiApiWhitelistCatalog.Catalog catalog = whitelistCatalog.snapshot();
        AiApiWhitelistCatalog.ServiceDefinition service = catalog.services().get(serviceId);
        if (service == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "未知 AI 工具服务: " + serviceId));
        }
        List<AiApiWhitelistCatalog.Operation> operations = catalog.operationsFor(serviceId);
        return webClient.get().uri(service.openapiUrl()).retrieve().bodyToMono(String.class)
                .map(body -> parse(body, service, operations))
                .onErrorMap(exception -> exception instanceof ResponseStatusException
                        ? exception
                        : new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "下游 OpenAPI 暂不可用: " + serviceId, exception));
    }

    private ObjectNode parse(String body, AiApiWhitelistCatalog.ServiceDefinition service,
                             List<AiApiWhitelistCatalog.Operation> operations) {
        try {
            JsonNode source = objectMapper.readTree(body);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("openapi", source.path("openapi").asText("3.0.1"));
            result.set("info", info(service));
            ArrayNode servers = result.putArray("servers");
            servers.addObject().put("url", properties.getPublicBaseUrl().replaceAll("/+$", ""));
            ObjectNode targetPaths = result.putObject("paths");
            for (AiApiWhitelistCatalog.Operation operation : operations) {
                JsonNode sourcePath = source.path("paths").path(operation.path());
                String method = operation.method().toLowerCase(Locale.ROOT);
                JsonNode sourceOperation = sourcePath.path(method);
                if (!sourceOperation.isObject()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "白名单接口未在下游 OpenAPI 中找到: " + operation.method() + " " + operation.path());
                }
                // 修改用途：业务 path 以 / 开头，不能使用会按 JSON Pointer 解释参数的 withObject。
                ObjectNode targetPath = targetPaths.path(operation.path()).isObject()
                        ? (ObjectNode) targetPaths.path(operation.path())
                        : targetPaths.putObject(operation.path());
                if (sourcePath.path("parameters").isArray()) {
                    targetPath.set("parameters", sourcePath.path("parameters").deepCopy());
                }
                ObjectNode targetOperation = sourceOperation.deepCopy();
                targetOperation.put("operationId", operation.id());
                targetOperation.put("summary", operation.summary());
                ArrayNode tags = targetOperation.putArray("tags");
                tags.add(operation.group());
                targetPath.set(method, targetOperation);
            }
            if (source.path("components").isObject()) {
                // 保留当前服务组件，避免裁剪 $ref 时重新实现一套 Schema 解析器；业务路径仍严格按白名单过滤。
                result.set("components", source.path("components").deepCopy());
            }
            return result;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "下游 OpenAPI 解析失败: " + service.id(), exception);
        }
    }

    private ObjectNode info(AiApiWhitelistCatalog.ServiceDefinition service) {
        ObjectNode info = objectMapper.createObjectNode();
        info.put("title", service.name());
        info.put("version", String.valueOf(whitelistCatalog.snapshot().version()));
        info.put("description", "由 WMS AI 唯一白名单生成；最终权限由当前用户 Spring Security 决定");
        return info;
    }
}
