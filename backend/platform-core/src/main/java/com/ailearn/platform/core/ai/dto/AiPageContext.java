package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

/** 业务页面传入 AI 的最小上下文，禁止携带整页数据或身份字段。 */
public record AiPageContext(@JsonProperty("entity_type") String entityType,
                            @JsonProperty("entity_id") String entityId,
                            @JsonProperty("entry_page_code") String entryPageCode) {
    private static final Set<String> ENTITY_TYPES = Set.of(
            "SALES_ORDER", "PURCHASE_ORDER", "WORK_ORDER", "DEVICE", "DEVICE_ALARM", "INVENTORY_BATCH");

    public AiPageContext {
        entityType = normalize(entityType);
        entityId = trim(entityId);
        entryPageCode = trim(entryPageCode);
        if (!entityType.isBlank() && !ENTITY_TYPES.contains(entityType)) {
            throw new IllegalArgumentException("page_context.entity_type 不受支持");
        }
        if (entityId.length() > 128 || entryPageCode.length() > 128) {
            throw new IllegalArgumentException("page_context 字段过长");
        }
    }

    private static String normalize(String value) {
        return trim(value).toUpperCase(java.util.Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
