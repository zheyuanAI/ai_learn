package com.ailearn.platform.iot.profile.dto;

import com.ailearn.platform.iot.profile.domain.DeviceProfile.MetricDefinition;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 设备模型响应，不泄露内部数据库细节。 */
public record DeviceProfileView(UUID id, @JsonProperty("profile_code") String profileCode,
                                @JsonProperty("profile_name") String profileName,
                                String status,
                                @JsonProperty("offline_timeout_seconds") int offlineTimeoutSeconds,
                                List<MetricDefinition> metrics,
                                @JsonProperty("created_at") OffsetDateTime createdAt,
                                List<AllowedAction> allowedActions) {
    public DeviceProfileView {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /** 前端可直接使用的后端动作能力。 */
    public record AllowedAction(String action, boolean enabled, String reason) {
    }
}
