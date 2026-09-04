package com.ailearn.platform.iot.device.domain;

/** 设备接入生命周期；在线/运行状态由后续遥测事实单独维护。 */
public enum DeviceLifecycleStatus {
    Active,
    Disabled;

    public static DeviceLifecycleStatus parse(String value) {
        if (value == null) {
            return null;
        }
        for (DeviceLifecycleStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }
}
