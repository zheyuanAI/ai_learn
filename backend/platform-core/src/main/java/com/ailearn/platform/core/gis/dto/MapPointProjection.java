package com.ailearn.platform.core.gis.dto;

import com.ailearn.platform.core.gis.domain.DisplayStatus;
import com.ailearn.platform.core.gis.domain.MapEntityType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** 当前用户可见的地图点位投影。 */
public record MapPointProjection(
        @JsonProperty("point_id") UUID pointId,
        @JsonProperty("entity_type") MapEntityType entityType,
        @JsonProperty("entity_id") UUID entityId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("x_percent") double xPercent,
        @JsonProperty("y_percent") double yPercent,
        double rotation,
        @JsonProperty("display_status") DisplayStatus displayStatus,
        @JsonProperty("linked_page") String linkedPage,
        @JsonProperty("source_updated_at") Instant sourceUpdatedAt,
        @JsonProperty("alarm_id") UUID alarmId,
        @JsonProperty("alarm_level") String alarmLevel,
        @JsonProperty("alarm_status") String alarmStatus,
        @JsonProperty("occurred_at") Instant occurredAt) {

    /** 兼容旧调用方；没有告警详情时扩展字段为空。 */
    public MapPointProjection(UUID pointId, MapEntityType entityType, UUID entityId,
                              String displayName, double xPercent, double yPercent,
                              double rotation, DisplayStatus displayStatus, String linkedPage,
                              Instant sourceUpdatedAt) {
        this(pointId, entityType, entityId, displayName, xPercent, yPercent, rotation,
                displayStatus, linkedPage, sourceUpdatedAt, null, null, null, null);
    }
}
