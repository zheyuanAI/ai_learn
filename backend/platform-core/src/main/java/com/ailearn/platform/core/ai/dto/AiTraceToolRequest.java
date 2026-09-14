package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Open WebUI 追溯工具的严格参数对象，不允许模型传入租户、用户或任意查询表达式。 */
@Schema(name = "AiTraceToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiTraceToolRequest(
        @JsonProperty("entity_type")
        @Schema(description = "业务对象类型", allowableValues = {
                "SALES_ORDER", "PURCHASE_ORDER", "WORK_ORDER", "DEVICE_ALARM", "INVENTORY_BATCH"})
        String entityType,
        @JsonProperty("entity_id")
        @Schema(description = "业务对象 UUID", format = "uuid")
        String entityId) {

    /** 转为现有工具执行器使用的参数结构，字段名与固定 JSON Schema 保持一致。 */
    public Map<String, Object> toArguments() {
        return Map.of("entity_type", entityType == null ? "" : entityType,
                "entity_id", entityId == null ? "" : entityId);
    }
}
