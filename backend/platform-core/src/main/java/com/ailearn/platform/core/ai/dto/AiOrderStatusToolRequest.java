package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/** Open WebUI 销售/采购订单状态工具的严格参数对象。 */
@Schema(name = "AiOrderStatusToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiOrderStatusToolRequest(
        @JsonProperty("order_id") @Schema(description = "订单 UUID；与 order_no 至少提供一个", format = "uuid")
        String orderId,
        @JsonProperty("order_no") @Schema(description = "订单业务单号；与 order_id 至少提供一个", maxLength = 128)
        String orderNo) {

    /** 转为固定工具参数并省略空字段。 */
    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        put(arguments, "order_id", orderId);
        put(arguments, "order_no", orderNo);
        return Map.copyOf(arguments);
    }

    private static void put(Map<String, Object> arguments, String name, String value) {
        if (value != null && !value.isBlank()) {
            arguments.put(name, value);
        }
    }
}
