package com.ailearn.platform.core.dashboard.domain;

import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;

/** 一期固定的七类看板摘要，禁止用户自定义统计公式。 */
public enum DashboardSummaryType {
    INVENTORY("inventory"),
    FULFILLMENT("fulfillment"),
    MANUFACTURING("manufacturing"),
    QUALITY("quality"),
    DEVICE("device"),
    ALARM("alarm"),
    TRACEABILITY("traceability");

    private final String key;

    DashboardSummaryType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String permission() {
        return "dashboard:" + key + ":view";
    }

    public static DashboardSummaryType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new GisException(GisErrorCode.GIS_QUERY_001, "summary_type 不能为空");
        }
        for (DashboardSummaryType type : values()) {
            if (type.key.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new GisException(GisErrorCode.GIS_QUERY_001, "不支持的摘要类型: " + value);
    }
}
