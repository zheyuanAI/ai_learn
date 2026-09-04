package com.ailearn.platform.core.gis.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 只保存二维位置和源实体引用的点位配置。 */
public record MapPointConfiguration(UUID id, UUID tenantId, UUID siteMapId, MapEntityType entityType,
                                    UUID entityId, double xPercent, double yPercent, double rotation,
                                    String linkedPage, Instant createdAt, Instant updatedAt) {

    public MapPointConfiguration {
        Objects.requireNonNull(id, "点位 id 不能为空");
        Objects.requireNonNull(tenantId, "点位 tenantId 不能为空");
        Objects.requireNonNull(siteMapId, "siteMapId 不能为空");
        Objects.requireNonNull(entityType, "entityType 不能为空");
        Objects.requireNonNull(entityId, "entityId 不能为空");
        if (!Double.isFinite(xPercent) || xPercent < 0 || xPercent > 100
                || !Double.isFinite(yPercent) || yPercent < 0 || yPercent > 100) {
            throw new IllegalArgumentException("点位坐标必须在 0 至 100 百分比之间");
        }
        if (!Double.isFinite(rotation) || rotation < -360 || rotation > 360) {
            throw new IllegalArgumentException("点位 rotation 必须在 -360 至 360 度之间");
        }
        linkedPage = linkedPage == null ? "" : linkedPage;
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        Objects.requireNonNull(updatedAt, "updatedAt 不能为空");
    }
}
