package com.ailearn.platform.core.ai.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** AI 审计摘要脱敏器，避免认证凭据或 Provider 密钥进入数据库。 */
@Component
public class AiAuditSanitizer {
    private static final int MAX_SUMMARY_LENGTH = 2000;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "secret", "token", "authorization", "api_key", "apikey", "credential");
    private final ObjectMapper objectMapper;

    /** 注入项目统一 Jackson 配置。 */
    public AiAuditSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 用途：将工具入参转换为有长度上限的脱敏 JSON。
     * 入参：模型给出的结构化参数；出参：安全审计摘要；流程：敏感键替换为固定标记，普通值截断。
     */
    public String summarize(Map<String, Object> arguments) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (arguments != null) {
            arguments.forEach((key, value) -> safe.put(key, sanitize(key, value)));
        }
        try {
            return truncate(objectMapper.writeValueAsString(safe), MAX_SUMMARY_LENGTH);
        } catch (JsonProcessingException exception) {
            return "{\"summary\":\"unavailable\"}";
        }
    }

    private static Object sanitize(String key, Object value) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains)) {
            return "[REDACTED]";
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> safeNested = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) -> {
                String textKey = String.valueOf(nestedKey);
                safeNested.put(textKey, sanitize(textKey, nestedValue));
            });
            return safeNested;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<Object> values = new java.util.ArrayList<>();
            for (Object item : iterable) {
                values.add(item instanceof String text ? truncate(text, MAX_VALUE_LENGTH) : item);
                if (values.size() >= 20) {
                    break;
                }
            }
            return values;
        }
        return value instanceof String text ? truncate(text, MAX_VALUE_LENGTH) : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
