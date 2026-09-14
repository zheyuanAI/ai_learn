package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 服务端 AI 只读工具白名单，模型不能注册或扩大工具集合。 */
@Component
public class AiToolRegistry {
    private final Map<String, AiReadTool> tools;
    private final Set<String> enabledToolNames;

    /**
     * 用途：收集 Spring 中明确实现的只读工具并构造不可变白名单。
     * 入参：工具 Bean 列表；出参：注册表；流程：规范化名称并拒绝重复或空名称。
     */
    @Autowired
    public AiToolRegistry(List<AiReadTool> registeredTools, AiProperties properties) {
        this(registeredTools, properties.getEnabledTools());
    }

    /** 测试和独立构造使用：未提供部署配置时启用全部代码注册工具。 */
    public AiToolRegistry(List<AiReadTool> registeredTools) {
        this(registeredTools, registeredTools.stream().map(AiReadTool::name).collect(Collectors.toSet()));
    }

    /**
     * 用途：构造代码固定工具集合与部署启用名单的交集。
     * 入参：代码注册工具和配置启用编码；流程：先拒绝重复，再拒绝配置中不存在的任意工具名。
     */
    public AiToolRegistry(List<AiReadTool> registeredTools, Set<String> configuredEnabledTools) {
        Map<String, AiReadTool> byName = new LinkedHashMap<>();
        for (AiReadTool tool : registeredTools) {
            String name = normalize(tool.name());
            if (byName.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("AI 工具名称重复: " + name);
            }
        }
        this.tools = Map.copyOf(byName);
        Set<String> normalizedEnabled = configuredEnabledTools == null
                ? Set.of()
                : configuredEnabledTools.stream().map(AiToolRegistry::normalize).collect(Collectors.toSet());
        Set<String> unknownNames = normalizedEnabled.stream()
                .filter(name -> !tools.containsKey(name))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!unknownNames.isEmpty()) {
            throw new IllegalStateException("AI 启用名单包含未注册工具: " + String.join(",", unknownNames));
        }
        this.enabledToolNames = Set.copyOf(normalizedEnabled);
    }

    /** 查询服务端已注册且当前用户有权使用的工具名称。 */
    public List<String> availableToolNames(AiRequestContext context) {
        return tools.entrySet().stream()
                .filter(entry -> enabledToolNames.contains(entry.getKey()))
                .filter(entry -> entry.getValue().isAllowed(context))
                .map(entry -> entry.getValue().name())
                .sorted()
                .toList();
    }

    /** 返回当前权限和客户端缩小白名单共同允许的工具实例。 */
    public List<AiReadTool> availableTools(AiRequestContext context, Set<String> requestedWhitelist) {
        Set<String> normalizedWhitelist = requestedWhitelist == null
                ? Set.of()
                : requestedWhitelist.stream().map(AiToolRegistry::normalize).collect(java.util.stream.Collectors.toSet());
        return tools.values().stream()
                .filter(tool -> enabledToolNames.contains(normalize(tool.name())))
                .filter(tool -> tool.isAllowed(context))
                .filter(tool -> normalizedWhitelist.isEmpty() || normalizedWhitelist.contains(normalize(tool.name())))
                .sorted(java.util.Comparator.comparing(AiReadTool::name))
                .toList();
    }

    /** 查找指定工具；未注册名称返回空，不允许动态反射执行。 */
    public Optional<AiReadTool> find(String name) {
        String normalizedName = normalize(name);
        if (!enabledToolNames.contains(normalizedName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(normalizedName));
    }

    private static String normalize(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AI 工具名称不能为空");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
