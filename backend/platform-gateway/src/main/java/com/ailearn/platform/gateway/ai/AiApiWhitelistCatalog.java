package com.ailearn.platform.gateway.ai;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.FileSystemResource;

/**
 * 读取 AI 业务 API 唯一白名单，并提供严格的 method + path 匹配。
 * 分组只服务目录可读性，角色和权限仍由下游 Spring Security 决定。
 */
@Component
public class AiApiWhitelistCatalog {
    private final GatewayAiProperties properties;
    private volatile Catalog catalog = new Catalog(0, Map.of(), List.of());

    public AiApiWhitelistCatalog(GatewayAiProperties properties) {
        this.properties = properties;
    }

    /** 启动时解析本地唯一白名单；AI 开启时缺失或非法配置直接阻止启动。 */
    @PostConstruct
    public void load() {
        Path path = resolvePath(properties.getApiWhitelistPath());
        if (!Files.isRegularFile(path)) {
            if (properties.isEnabled()) {
                throw new IllegalStateException("AI API 白名单不存在: " + path);
            }
            return;
        }
        this.catalog = parse(new FileSystemResource(path));
    }

    /** 返回白名单快照，调用方不得修改。 */
    public Catalog snapshot() {
        return catalog;
    }

    /** 按真实请求方法和路径查找唯一登记操作。 */
    public Optional<Operation> find(String method, String requestPath) {
        if (!StringUtils.hasText(method) || !StringUtils.hasText(requestPath)) {
            return Optional.empty();
        }
        String normalizedMethod = method.trim().toUpperCase(Locale.ROOT);
        return catalog.operations().stream()
                .filter(operation -> operation.method().equals(normalizedMethod))
                .filter(operation -> matches(operation.path(), requestPath))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    Catalog parse(FileSystemResource resource) {
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        Map<String, Object> root = factory.getObject();
        if (root == null) {
            throw new IllegalStateException("AI API 白名单为空");
        }
        int version = integer(root.get("version"), "version");
        if (version != 1) {
            throw new IllegalStateException("不支持的 AI API 白名单版本: " + version);
        }

        Map<String, ServiceDefinition> services = new LinkedHashMap<>();
        Map<String, Object> rawServices = map(root.get("services"), "services");
        rawServices.forEach((serviceId, value) -> {
            Map<String, Object> item = map(value, "services." + serviceId);
            String normalizedId = identifier(serviceId, "service id");
            ServiceDefinition definition = new ServiceDefinition(normalizedId,
                    text(item.get("openapi-url"), "services." + serviceId + ".openapi-url"),
                    normalizedPath(text(item.get("catalog-path"),
                            "services." + serviceId + ".catalog-path"), false),
                    text(item.get("name"), "services." + serviceId + ".name"));
            if (services.putIfAbsent(normalizedId, definition) != null) {
                throw new IllegalStateException("AI API 白名单服务重复: " + normalizedId);
            }
        });
        if (services.isEmpty()) {
            throw new IllegalStateException("AI API 白名单至少需要一个服务");
        }

        List<Operation> operations = new ArrayList<>();
        Set<String> operationIds = new LinkedHashSet<>();
        Set<String> routes = new LinkedHashSet<>();
        Map<String, Object> groups = map(root.get("groups"), "groups");
        groups.forEach((groupId, value) -> {
            Map<String, Object> group = map(value, "groups." + groupId);
            String description = text(group.get("description"), "groups." + groupId + ".description");
            Object rawOperations = group.get("operations");
            if (!(rawOperations instanceof List<?> list) || list.isEmpty()) {
                throw new IllegalStateException("AI API 白名单分组没有操作: " + groupId);
            }
            for (Object rawOperation : list) {
                Map<String, Object> item = map(rawOperation, "groups." + groupId + ".operations");
                String id = identifier(text(item.get("id"), "operation.id"), "operation id");
                String service = identifier(text(item.get("service"), "operation.service"), "operation service");
                if (!services.containsKey(service)) {
                    throw new IllegalStateException("AI API 操作引用未知服务: " + service);
                }
                String method = text(item.get("method"), "operation.method").toUpperCase(Locale.ROOT);
                if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                    throw new IllegalStateException("AI API 操作方法无效: " + method);
                }
                String path = normalizedPath(text(item.get("path"), "operation.path"), true);
                String summary = text(item.get("summary"), "operation.summary");
                if (!operationIds.add(id.toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("AI API operation id 重复: " + id);
                }
                if (!routes.add(method + " " + path)) {
                    throw new IllegalStateException("AI API method + path 重复: " + method + " " + path);
                }
                operations.add(new Operation(id, service, groupId, description, method, path, summary));
            }
        });
        if (operations.isEmpty()) {
            throw new IllegalStateException("AI API 白名单至少需要一个操作");
        }
        return new Catalog(version, Map.copyOf(services), List.copyOf(operations));
    }

    private static boolean matches(String template, String requestPath) {
        String[] expected = trimSlashes(template).split("/", -1);
        String[] actual = trimSlashes(requestPath).split("/", -1);
        if (expected.length != actual.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            String segment = expected[index];
            if (segment.startsWith("{") && segment.endsWith("}")) {
                if (actual[index].isBlank()) {
                    return false;
                }
            } else if (!segment.equals(actual[index])) {
                return false;
            }
        }
        return true;
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+|/+$", "");
    }

    private static Path resolvePath(String configured) {
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException("WMS_AI_API_WHITELIST_PATH 不能为空");
        }
        Path path = Path.of(configured.trim());
        return path.isAbsolute() ? path.normalize() : Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private static String normalizedPath(String value, boolean businessPath) {
        String path = value.trim();
        if (!path.startsWith("/") || path.contains("*") || path.contains("//")
                || path.contains("?") || path.contains("#")) {
            throw new IllegalStateException("AI API 路径格式无效: " + value);
        }
        if (businessPath && !path.startsWith("/api/")) {
            throw new IllegalStateException("AI API 只允许登记 /api/** 业务路径: " + value);
        }
        for (String segment : trimSlashes(path).split("/")) {
            if ((segment.contains("{") || segment.contains("}"))
                    && !segment.matches("\\{[A-Za-z][A-Za-z0-9_]*}")) {
                throw new IllegalStateException("AI API 路径变量格式无效: " + value);
            }
        }
        return path;
    }

    private static String identifier(String value, String name) {
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_-]{0,127}")) {
            throw new IllegalStateException(name + " 格式无效: " + value);
        }
        return normalized;
    }

    private static String text(Object value, String name) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalStateException(name + " 不能为空");
        }
        return text;
    }

    private static int integer(Object value, String name) {
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value, name));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " 必须是整数", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String name) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException(name + " 必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    public record Catalog(int version, Map<String, ServiceDefinition> services, List<Operation> operations) {
        public List<Operation> operationsFor(String service) {
            return operations.stream().filter(operation -> operation.service().equals(service)).toList();
        }
    }

    public record ServiceDefinition(String id, String openapiUrl, String catalogPath, String name) {
    }

    public record Operation(String id, String service, String group, String groupDescription,
                            String method, String path, String summary) {
    }
}
