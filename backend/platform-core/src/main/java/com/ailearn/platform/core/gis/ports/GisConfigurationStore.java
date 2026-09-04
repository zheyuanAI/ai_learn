package com.ailearn.platform.core.gis.ports;

import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** GIS 自有展示配置存储端口；不负责读取任何源业务事实。 */
public interface GisConfigurationStore {
    SiteMapConfiguration saveMap(SiteMapConfiguration map);

    /**
     * 保存带幂等元数据的点位配置。
     * 入参：已通过应用层校验的点位、当前租户范围内的幂等键和服务端载荷摘要；出参：首次写入或同载荷重放的点位。
     * 默认实现保持旧的测试存储兼容，生产适配器必须把幂等元数据落到 V6 表中。
     *
     * @param point 点位配置
     * @param idempotencyKey 当前租户幂等键
     * @param payloadDigest 服务端载荷摘要
     * @return 已保存点位
     */
    default MapPointConfiguration savePoint(MapPointConfiguration point, String idempotencyKey,
                                            String payloadDigest) {
        return savePoint(point);
    }

    MapPointConfiguration savePoint(MapPointConfiguration point);

    /**
     * 按当前租户和幂等键读取已保存点位，供服务重启后的安全重放使用。
     *
     * @param tenantId 可信租户
     * @param idempotencyKey 幂等键
     * @return 已保存点位及其服务端摘要
     */
    default Optional<MapPointIdempotencyRecord> findPointByIdempotencyKey(UUID tenantId,
                                                                           String idempotencyKey) {
        return Optional.empty();
    }

    Optional<SiteMapConfiguration> findMap(UUID tenantId, UUID mapId);
    List<SiteMapConfiguration> findMaps(UUID tenantId);
    List<MapPointConfiguration> findPoints(UUID tenantId, UUID mapId);
    Optional<MapPointConfiguration> findPoint(UUID tenantId, UUID pointId);
}
