package com.ailearn.platform.iot.device.domain.port;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

/** 设备持久化端口；通过 tenantId 约束所有读取和写入范围。 */
public interface DeviceRepository {
    boolean existsDeviceByCode(UUID tenantId, String deviceCode);
    Device insert(Device device);
    Optional<Device> findDeviceById(UUID tenantId, UUID id);
    Optional<Device> findByCode(UUID tenantId, String deviceCode);
    List<Device> findPage(UUID tenantId, String code, DeviceLifecycleStatus status, int offset, int limit);
    long count(UUID tenantId, String code, DeviceLifecycleStatus status);
    Device updateLifecycle(UUID tenantId, UUID id, DeviceLifecycleStatus expected, DeviceLifecycleStatus target,
                           UUID operatorId, OffsetDateTime updatedAt);
    boolean hasHistoricalFacts(UUID tenantId, UUID deviceId);
}
