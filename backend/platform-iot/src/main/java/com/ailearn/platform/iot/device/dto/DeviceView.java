package com.ailearn.platform.iot.device.dto;

import com.ailearn.platform.iot.device.domain.Device;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 设备管理响应；不把 IoT 运行快照与设备身份混为一体。 */
public record DeviceView(UUID id, @JsonProperty("device_code") String deviceCode,
                         @JsonProperty("device_name") String deviceName,
                         @JsonProperty("device_profile_id") UUID deviceProfileId,
                         @JsonProperty("protocol_type") String protocolType,
                         @JsonProperty("lifecycle_status") String lifecycleStatus,
                         @JsonProperty("work_center_id") UUID workCenterId,
                         @JsonProperty("area_id") UUID areaId,
                         @JsonProperty("map_point_id") UUID mapPointId,
                         @JsonProperty("created_at") OffsetDateTime createdAt,
                         List<AllowedAction> allowedActions) {
    public DeviceView {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public static DeviceView from(Device device, List<AllowedAction> actions) {
        return new DeviceView(device.id(), device.deviceCode(), device.deviceName(), device.deviceProfileId(),
                device.protocolType(), device.lifecycleStatus().name(), device.workCenterId(), device.areaId(),
                device.mapPointId(), device.createdAt(), actions);
    }

    /** 后端计算的设备动作能力。 */
    public record AllowedAction(String action, boolean enabled, String reason) {
    }
}
