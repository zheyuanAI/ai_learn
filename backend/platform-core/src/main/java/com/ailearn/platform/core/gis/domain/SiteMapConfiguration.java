package com.ailearn.platform.core.gis.domain;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 当前租户的一张二维地图配置。
 * <p>
 * 本对象只描述 GIS 自有的底图及租户归属，不承载仓库、生产区域或设备的业务事实；点位引用和事实状态由独立配置/查询端口提供。
 * </p>
 */
public record SiteMapConfiguration(UUID id, UUID tenantId, String mapCode, String mapName,
                                   MapAssetMetadata asset, Instant createdAt, Instant updatedAt) {

    /**
     * 创建地图配置并校验其身份、租户归属、编码、底图和时间字段；这里只保证 GIS 配置自身完整，不验证被点位引用的业务实体。
     */
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

    /**
     * 判断地图是否属于可信查询上下文的租户；空上下文直接返回 false，避免把缺少租户凭据当成可见配置。
     */
    public boolean belongsTo(FactsQueryContext context) {
        return context != null && tenantId.equals(context.tenantId());
    }
}
