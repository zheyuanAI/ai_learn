package com.ailearn.platform.core.ai.tool;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** AI 查询工具共用的严格参数读取器，只负责格式与白名单校验，不承载业务判断。 */
final class AiToolArguments {
    private AiToolArguments() { }

    static Map<String, Object> safe(Map<String, Object> arguments) {
        return arguments == null ? Map.of() : arguments;
    }

    static void rejectUnknown(Map<String, Object> arguments, Set<String> allowed, String toolName) {
        if (!allowed.containsAll(arguments.keySet())) {
            throw new IllegalArgumentException(toolName + " 包含未允许参数");
        }
    }

    static String requiredText(Map<String, Object> arguments, String name) {
        String value = optionalText(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    static String optionalText(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    static UUID requiredUuid(Map<String, Object> arguments, String name) {
        String value = requiredText(arguments, name);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " 必须是有效 UUID", exception);
        }
    }

    static UUID optionalUuid(Map<String, Object> arguments, String name) {
        String value = optionalText(arguments, name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " 必须是有效 UUID", exception);
        }
    }

    static int limit(Map<String, Object> arguments, int defaultValue, int maximum) {
        Object value = arguments.get("limit");
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
            if (parsed < 1 || parsed > maximum) {
                throw new IllegalArgumentException("limit 必须在 1 到 " + maximum + " 之间");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("limit 必须是整数", exception);
        }
    }

    /** 读取并校验受控时间范围，避免模型构造任意区间触发大范围查询。 */
    static String timeRange(Map<String, Object> arguments, Set<String> allowed, String defaultValue) {
        String value = optionalText(arguments, "time_range");
        String normalized = value == null ? defaultValue : value.toLowerCase(java.util.Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("time_range 只允许 " + String.join("、", allowed));
        }
        return normalized;
    }
}
