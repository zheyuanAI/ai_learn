package com.ailearn.platform.iot.device.application;

import com.ailearn.platform.iot.device.dto.DeviceCreateRequest;
import com.ailearn.platform.iot.device.dto.DeviceLifecycleRequest;
import com.ailearn.platform.iot.device.dto.DevicePageResult;
import com.ailearn.platform.iot.device.dto.DeviceView;
import java.util.UUID;

/** 设备身份与生命周期管理端口。 */
public interface DeviceApplicationService {
    DeviceView create(DeviceCreateRequest request, String idempotencyKey);
    DevicePageResult page(String deviceCode, String lifecycleStatus, int page, int size);
    DeviceView detail(UUID id);
    DeviceView changeLifecycle(UUID id, DeviceLifecycleRequest request, String idempotencyKey);
}
