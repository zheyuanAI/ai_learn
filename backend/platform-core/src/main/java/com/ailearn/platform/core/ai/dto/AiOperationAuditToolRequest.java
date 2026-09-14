package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/** Open WebUI 操作审计工具的严格参数对象。 */
@Schema(name = "AiOperationAuditToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiOperationAuditToolRequest(
        @JsonProperty("entity_type")
        @Schema(description = "当前只支持销售订单", allowableValues = "SALES_ORDER")
        String entityType,
        @JsonProperty("entity_id")
        @Schema(description = "销售订单 UUID", format = "uuid")
        String entityId,
        @JsonProperty("occurred_from")
        @Schema(description = "可选起始时间，必须包含时区", format = "date-time")
        String occurredFrom,
        @JsonProperty("occurred_to")
        @Schema(description = "可选结束时间，必须包含时区", format = "date-time")
        String occurredTo,
        @Schema(description = "最多返回条数", minimum = "1", maximum = "100", defaultValue = "50")
        Integer limit) {

    /** 转为工具执行器参数，省略未提供的可选字段。 */
    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("entity_type", entityType == null ? "" : entityType);
        arguments.put("entity_id", entityId == null ? "" : entityId);
        putIfPresent(arguments, "occurred_from", occurredFrom);
        putIfPresent(arguments, "occurred_to", occurredTo);
        if (limit != null) {
            arguments.put("limit", limit);
        }
        return Map.copyOf(arguments);
    }

    private static void putIfPresent(Map<String, Object> arguments, String name, String value) {
        if (value != null && !value.isBlank()) {
            arguments.put(name, value);
        }
    }
}
