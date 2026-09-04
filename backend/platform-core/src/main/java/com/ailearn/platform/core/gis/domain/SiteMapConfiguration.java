package com.ailearn.platform.core.gis.domain;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 当前租户的一张二维地图配置。 */
public record SiteMapConfiguration(UUID id, UUID tenantId, String mapCode, String mapName,
                                   MapAssetMetadata asset, Instant createdAt, Instant updatedAt) {

    public SiteMapConfiguration {
        Objects.requireNonNull(id, "地图 id 不能为空");
        Objects.requireNonNull(tenantId, "地图 tenantId 不能为空");
        if (mapCode == null || mapCode.isBlank()) {
            throw new IllegalArgumentException("mapCode 不能为空");
        }
        if (mapName == null || mapName.isBlank()) {
            throw new IllegalArgumentException("mapName 不能为空");
        }
        Objects.requireNonNull(asset, "底图不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
    }

    public boolean belongsTo(FactsQueryContext context) {
        return context != null && tenantId.equals(context.tenantId());
    }
}
