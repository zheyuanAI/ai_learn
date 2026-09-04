package com.ailearn.platform.iot.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 设备 Active/Disabled 生命周期变更请求。 */
public record DeviceLifecycleRequest(@JsonProperty("lifecycle_status") String lifecycleStatus) {
}
