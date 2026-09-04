package com.ailearn.platform.iot.profile.domain;

/** 设备指标白名单支持的值类型。 */
public enum MetricValueType {
    NUMBER,
    BOOLEAN,
    TEXT;

    public static MetricValueType parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return value.trim().toUpperCase(java.util.Locale.ROOT).equals("NUMERIC")
                    ? NUMBER : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
