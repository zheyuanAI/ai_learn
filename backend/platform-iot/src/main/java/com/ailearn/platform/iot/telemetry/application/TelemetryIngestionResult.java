package com.ailearn.platform.iot.telemetry.application;

import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import java.util.List;
import java.util.UUID;

/**
 * 遥测摄取结果。
 * accepted 对新消息和相同载荷重复消息均为 true，duplicate 用于区分是否实际新增了事实。
 */
public record TelemetryIngestionResult(boolean accepted, boolean duplicate, String messageKey,
                                       List<UUID> telemetryIds, DeviceStatus status) {

    /**
     * 用途：冻结返回的遥测事实标识，防止调用方修改结果。
     */
    public TelemetryIngestionResult {
        telemetryIds = telemetryIds == null ? List.of() : List.copyOf(telemetryIds);
    }
}
