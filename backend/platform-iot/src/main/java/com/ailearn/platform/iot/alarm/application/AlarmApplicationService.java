package com.ailearn.platform.iot.alarm.application;

import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.dto.AlarmPageResult;
import com.ailearn.platform.iot.alarm.dto.AlarmView;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 告警查询与人工确认应用服务。 */
public interface AlarmApplicationService {
    AlarmPageResult page(UUID deviceId, AlarmStatus status, String alarmLevel, OffsetDateTime from,
                         OffsetDateTime to, String contextStatus, int page, int size);

    AlarmView detail(UUID alarmId);

    AlarmView ack(UUID alarmId, String ackComment, String idempotencyKey);
}
