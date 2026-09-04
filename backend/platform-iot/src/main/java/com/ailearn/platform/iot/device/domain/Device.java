package com.ailearn.platform.iot.device.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 实际设备聚合；设备不保存当前工单作为主关系。 */
public record Device(UUID id, UUID tenantId, String deviceCode, String deviceName,
                     UUID deviceProfileId, String protocolType, DeviceLifecycleStatus lifecycleStatus,
                     UUID workCenterId, UUID areaId, UUID mapPointId,
                     UUID createdBy, OffsetDateTime createdAt, UUID updatedBy, OffsetDateTime updatedAt) {
}
