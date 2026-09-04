package com.ailearn.platform.iot.profile.application;

import com.ailearn.platform.iot.profile.dto.AlarmRuleCreateRequest;
import com.ailearn.platform.iot.profile.dto.AlarmRuleView;
import com.ailearn.platform.iot.profile.dto.DeviceProfileCreateRequest;
import com.ailearn.platform.iot.profile.dto.DeviceProfilePageResult;
import com.ailearn.platform.iot.profile.dto.DeviceProfileView;
import java.util.UUID;

/** 设备模型与一期单指标告警规则应用端口。 */
public interface DeviceProfileApplicationService {
    DeviceProfileView create(DeviceProfileCreateRequest request, String idempotencyKey);
    DeviceProfilePageResult page(String profileCode, int page, int size);
    DeviceProfileView detail(UUID id);
    AlarmRuleView createRule(AlarmRuleCreateRequest request, String idempotencyKey);
    java.util.List<AlarmRuleView> rules(UUID profileId, int page, int size);
}
