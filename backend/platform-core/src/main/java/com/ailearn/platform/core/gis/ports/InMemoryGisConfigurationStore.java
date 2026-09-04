package com.ailearn.platform.core.gis.ports;

import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** GIS 应用层默认测试存储，生产持久化适配器可在本模块后续接入。 */
public class InMemoryGisConfigurationStore implements GisConfigurationStore {
    private final ConcurrentMap<UUID, SiteMapConfiguration> maps = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MapPointConfiguration> points = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MapPointIdempotencyRecord> idempotentPoints = new ConcurrentHashMap<>();

    @Override
    public SiteMapConfiguration saveMap(SiteMapConfiguration map) {
        maps.put(map.id(), map);
        return map;
    }

    @Override
    public MapPointConfiguration savePoint(MapPointConfiguration point) {
        points.put(point.id(), point);
        return point;
    }

    /** 保存测试用点位及其幂等元数据，模拟数据库唯一键的重放行为。 */
    @Override
    public MapPointConfiguration savePoint(MapPointConfiguration point, String idempotencyKey,
                                           String payloadDigest) {
        String key = point.tenantId() + ":" + idempotencyKey;
        MapPointIdempotencyRecord record = new MapPointIdempotencyRecord(point, payloadDigest);
        MapPointIdempotencyRecord previous = idempotentPoints.putIfAbsent(key, record);
        if (previous != null) {
            if (!previous.payloadDigest().equals(record.payloadDigest())) {
                throw new GisException(GisErrorCode.GIS_POINT_002, "同一幂等键的点位载荷不一致");
            }
            return previous.point();
        }
        points.put(point.id(), point);
        return point;
    }

    /** 查询测试存储中的当前租户点位幂等记录。 */
    @Override
    public Optional<MapPointIdempotencyRecord> findPointByIdempotencyKey(UUID tenantId,
                                                                          String idempotencyKey) {
        return Optional.ofNullable(idempotentPoints.get(tenantId + ":" + idempotencyKey));
    }

    @Override
    public Optional<SiteMapConfiguration> findMap(UUID tenantId, UUID mapId) {
        return Optional.ofNullable(maps.get(mapId)).filter(map -> map.tenantId().equals(tenantId));
    }

    @Override
    public List<SiteMapConfiguration> findMaps(UUID tenantId) {
        return maps.values().stream().filter(map -> map.tenantId().equals(tenantId)).toList();
    }

    @Override
    public List<MapPointConfiguration> findPoints(UUID tenantId, UUID mapId) {
        return new ArrayList<>(points.values().stream()
                .filter(point -> point.tenantId().equals(tenantId) && point.siteMapId().equals(mapId))
                .toList());
    }

    @Override
    public Optional<MapPointConfiguration> findPoint(UUID tenantId, UUID pointId) {
        return Optional.ofNullable(points.get(pointId)).filter(point -> point.tenantId().equals(tenantId));
    }
}
