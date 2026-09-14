package com.ailearn.platform.core.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/** Open WebUI 设备告警摘要工具的严格参数对象。 */
@Schema(name = "AiDeviceAlarmToolRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AiDeviceAlarmToolRequest(
        @JsonProperty("time_range")
        @Schema(description = "受控时间范围", allowableValues = {"today", "7d", "30d"},
                defaultValue = "today")
        String timeRange,
        @JsonProperty("device_id")
        @Schema(description = "可选设备 UUID", format = "uuid")
        String deviceId) {

    /** 转为固定工具参数并省略空值。 */
    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        put(arguments, "time_range", timeRange);
        put(arguments, "device_id", deviceId);
        return Map.copyOf(arguments);
    }

    private static void put(Map<String, Object> arguments, String name, String value) {
        if (value != null && !value.isBlank()) {
            arguments.put(name, value.trim());
        }
    }
}
