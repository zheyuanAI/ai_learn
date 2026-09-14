package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/** Open WebUI 低库存工具的严格参数对象。 */
@Schema(name = "AiLowStockToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiLowStockToolRequest(
        @JsonProperty("warehouse_id")
        @Schema(description = "可选仓库 UUID", format = "uuid")
        String warehouseId,
        @Schema(description = "最多返回条数", minimum = "1", maximum = "100", defaultValue = "50")
        Integer limit) {

    /** 转为固定工具参数并省略空值。 */
    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (warehouseId != null && !warehouseId.isBlank()) {
            arguments.put("warehouse_id", warehouseId.trim());
        }
        if (limit != null) {
            arguments.put("limit", limit);
        }
        return Map.copyOf(arguments);
    }
}
