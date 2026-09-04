package com.ailearn.platform.iot.profile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 创建设备模型请求；tenant_id 和审计人不允许由客户端传入。 */
public record DeviceProfileCreateRequest(
        @JsonProperty("profile_code") String profileCode,
        @JsonProperty("profile_name") String profileName,
        List<MetricDefinitionRequest> metrics,
        @JsonProperty("offline_timeout_seconds") Integer offlineTimeoutSeconds) {
}
