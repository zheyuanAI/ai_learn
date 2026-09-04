package com.ailearn.platform.iot.alarm.dto;

import java.util.List;

/** 告警分页响应。 */
public record AlarmPageResult(List<AlarmView> records, long total, int page, int size) {
    public AlarmPageResult {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
