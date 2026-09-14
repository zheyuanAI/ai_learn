package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Open WebUI 工单进度工具的严格参数对象。 */
@Schema(name = "AiWorkOrderToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiWorkOrderToolRequest(
        @JsonProperty("work_order_id")
        @Schema(description = "工单 UUID", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        String workOrderId) {

    /** 转为固定工具参数。 */
    public Map<String, Object> toArguments() {
        return Map.of("work_order_id", workOrderId == null ? "" : workOrderId);
    }
}
