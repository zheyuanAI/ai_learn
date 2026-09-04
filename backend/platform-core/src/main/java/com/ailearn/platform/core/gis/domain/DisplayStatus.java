package com.ailearn.platform.core.gis.domain;

/** 点位展示状态，不驱动任何源业务状态迁移。 */
public enum DisplayStatus {
    NORMAL,
    WARNING,
    ALARM,
    OFFLINE;

    /** 同时满足多个状态时按业务规则计算最高优先级。 */
    public static DisplayStatus highest(boolean alarm, boolean offline, boolean warning) {
        if (alarm) {
            return ALARM;
        }
        if (offline) {
            return OFFLINE;
        }
        if (warning) {
            return WARNING;
        }
        return NORMAL;
    }
}
