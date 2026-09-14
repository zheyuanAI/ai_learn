package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/** Open WebUI 库存余额工具的严格参数对象。 */
@Schema(name = "AiInventoryToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiInventoryToolRequest(
        @JsonProperty("product_id") @Schema(description = "产品 UUID", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @JsonProperty("warehouse_id") @Schema(description = "可选仓库 UUID", format = "uuid")
        String warehouseId,
        @JsonProperty("location_id") @Schema(description = "可选库位 UUID", format = "uuid")
        String locationId,
        @JsonProperty("lot_no") @Schema(description = "可选批次号", maxLength = 128)
        String lotNo,
        @Schema(description = "最多返回条数", minimum = "1", maximum = "100", defaultValue = "50")
        Integer limit) {

    /** 转为固定工具参数并省略空的可选字段。 */
    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("product_id", productId == null ? "" : productId);
        put(arguments, "warehouse_id", warehouseId);
        put(arguments, "location_id", locationId);
        put(arguments, "lot_no", lotNo);
        if (limit != null) {
            arguments.put("limit", limit);
        }
        return Map.copyOf(arguments);
    }

    private static void put(Map<String, Object> arguments, String name, String value) {
        if (value != null && !value.isBlank()) {
            arguments.put(name, value);
        }
    }
}
