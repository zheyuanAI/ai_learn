package com.ailearn.platform.iot.device.dto;

import java.util.List;

/** 设备分页响应。 */
public record DevicePageResult(List<DeviceView> records, long total, int page, int size) {
    public DevicePageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
