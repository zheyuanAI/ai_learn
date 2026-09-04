package com.ailearn.platform.core.gis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 二维底图和当前用户权限范围内的点位投影。 */
public record SiteMapProjection(@JsonProperty("site_map_id") UUID siteMapId,
                                @JsonProperty("map_code") String mapCode,
                                @JsonProperty("map_name") String mapName,
                                @JsonProperty("background_type") String backgroundType,
                                @JsonProperty("storage_key") String storageKey,
                                List<MapPointProjection> points,
                                @JsonProperty("generated_at") Instant generatedAt,
                                @JsonProperty("request_id") String requestId) {
    public SiteMapProjection {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
