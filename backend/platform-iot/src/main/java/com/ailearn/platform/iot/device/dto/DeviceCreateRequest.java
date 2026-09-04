package com.ailearn.platform.iot.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** 创建设备请求；租户和审计字段只从可信请求上下文获取。 */
public record DeviceCreateRequest(
        @JsonProperty("device_code") String deviceCode,
        @JsonProperty("device_name") String deviceName,
        @JsonProperty("device_profile_id") UUID deviceProfileId,
        @JsonProperty("protocol_type") String protocolType,
        @JsonProperty("work_center_id") UUID workCenterId,
        @JsonProperty("area_id") UUID areaId,
        @JsonProperty("map_point_id") UUID mapPointId) {
}
