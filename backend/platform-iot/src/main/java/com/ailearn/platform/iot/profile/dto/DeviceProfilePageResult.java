package com.ailearn.platform.iot.profile.dto;

import java.util.List;

/** 设备模型分页响应。 */
public record DeviceProfilePageResult(List<DeviceProfileView> records, long total, int page, int size) {
    public DeviceProfilePageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
