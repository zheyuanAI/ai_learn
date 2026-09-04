package com.ailearn.platform.core.gis.application;

import com.ailearn.platform.core.gis.domain.DisplayStatus;
import com.ailearn.platform.core.gis.domain.MapEntityType;
import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import com.ailearn.platform.core.gis.dto.CreateSiteMapCommand;
import com.ailearn.platform.core.gis.dto.MapPointProjection;
import com.ailearn.platform.core.gis.dto.SaveMapPointCommand;
import com.ailearn.platform.core.gis.dto.SiteMapProjection;
import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.gis.ports.GisConfigurationStore;
import com.ailearn.platform.core.gis.ports.MapPointIdempotencyRecord;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactQueryUnavailableException;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.PointStatusFacts;
import com.ailearn.platform.core.traceability.ports.ReferencedEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * GIS 地图和点位配置应用服务。
 * <p>
 * 本服务只写入 GIS 自有展示配置；源仓库、生产区域和设备均通过最小 Facts 端口读取。
 * </p>
 */
public class GisApplicationService {
    public static final String MAP_VIEW_PERMISSION = "gis:map:view";
    /** 与 Auth V6 中的稳定权限码一致，避免使用未播种的 config 别名导致生产写接口永远被拒绝。 */
    public static final String MAP_CONFIG_PERMISSION = "gis:map:manage";

    private final GisConfigurationStore store;
    private final InventoryFactsQuery inventoryFacts;
    private final ManufacturingFactsQuery manufacturingFacts;
    private final IotFactsPort iotFacts;
    private final Clock clock;
    private final ConcurrentMap<String, IdempotentPoint> idempotentPoints = new ConcurrentHashMap<>();

    public GisApplicationService(GisConfigurationStore store, InventoryFactsQuery inventoryFacts,
                                 ManufacturingFactsQuery manufacturingFacts, IotFactsPort iotFacts) {
        this(store, inventoryFacts, manufacturingFacts, iotFacts, Clock.systemUTC());
    }

    public GisApplicationService(GisConfigurationStore store, InventoryFactsQuery inventoryFacts,
                                 ManufacturingFactsQuery manufacturingFacts, IotFactsPort iotFacts,
                                 Clock clock) {
        this.store = store;
        this.inventoryFacts = inventoryFacts;
        this.manufacturingFacts = manufacturingFacts;
        this.iotFacts = iotFacts;
        this.clock = clock;
    }

    /** 创建当前租户地图；入参不含且不能覆盖可信租户。 */
    public SiteMapConfiguration createMap(FactsQueryContext context, CreateSiteMapCommand command) {
        requirePermission(context, MAP_CONFIG_PERMISSION);
        if (command == null || command.mapCode() == null || command.mapCode().isBlank()
                || command.mapName() == null || command.mapName().isBlank() || command.asset() == null) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "地图编码、名称和底图均不能为空");
        }
        boolean duplicate = store.findMaps(context.tenantId()).stream()
                .anyMatch(map -> map.mapCode().equals(command.mapCode().trim()));
        if (duplicate) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "当前租户地图编码已存在");
        }
        Instant now = clock.instant();
        return store.saveMap(new SiteMapConfiguration(UUID.randomUUID(), context.tenantId(),
                command.mapCode().trim(), command.mapName().trim(), command.asset(), now, now));
    }

    /** 查询当前租户可见的地图列表。 */
    public List<SiteMapConfiguration> listMaps(FactsQueryContext context) {
        requirePermission(context, MAP_VIEW_PERMISSION);
        return store.findMaps(context.tenantId());
    }

    /**
     * 保存点位配置并支持同租户幂等重放。
     * 入参：可信上下文、点位命令和 Idempotency-Key；出参：保存后的点位配置；流程：权限校验、地图/实体校验、坐标校验、幂等保存。
     */
    public MapPointConfiguration savePoint(FactsQueryContext context, SaveMapPointCommand command,
                                           String idempotencyKey) {
        requirePermission(context, MAP_CONFIG_PERMISSION);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "点位写入必须提供 Idempotency-Key");
        }
        validatePointCommand(command);
        String digest = digest(command);
        String key = context.tenantId() + ":" + idempotencyKey.trim();
        IdempotentPoint previous = idempotentPoints.get(key);
        if (previous != null) {
            if (!previous.digest().equals(digest)) {
                throw new GisException(GisErrorCode.GIS_POINT_002, "同一幂等键的点位载荷不一致");
            }
            return previous.point();
        }
        MapPointIdempotencyRecord persisted = store.findPointByIdempotencyKey(
                context.tenantId(), idempotencyKey.trim()).orElse(null);
        if (persisted != null) {
            if (!persisted.payloadDigest().equals(digest)) {
                throw new GisException(GisErrorCode.GIS_POINT_002, "同一幂等键的点位载荷不一致");
            }
            idempotentPoints.putIfAbsent(key, new IdempotentPoint(digest, persisted.point()));
            return persisted.point();
        }
        SiteMapConfiguration map = store.findMap(context.tenantId(), command.siteMapId())
                .orElseThrow(() -> new GisException(GisErrorCode.GIS_TENANT_001, "地图不属于当前租户"));
        ReferencedEntity entity = resolveEntity(context, command.entityType(), command.entityId());
        if (!entity.visible() || !entity.tenantId().equals(context.tenantId())) {
            throw new GisException(GisErrorCode.GIS_TENANT_001, "点位实体不属于当前租户或当前用户不可见");
        }
        Instant now = clock.instant();
        MapPointConfiguration point = new MapPointConfiguration(UUID.randomUUID(), map.tenantId(), map.id(),
                command.entityType(), command.entityId(), command.xPercent(), command.yPercent(),
                command.rotation(), command.linkedPage(), now, now);
        MapPointConfiguration saved = store.savePoint(point, idempotencyKey.trim(), digest);
        IdempotentPoint raced = idempotentPoints.putIfAbsent(key, new IdempotentPoint(digest, saved));
        return raced == null ? saved : raced.point();
    }

    /** 查询地图及当前用户可见点位；status 过滤在投影层完成。 */
    public SiteMapProjection getSiteMap(FactsQueryContext context, UUID siteMapId,
                                        MapEntityType entityType, DisplayStatus status) {
        requirePermission(context, MAP_VIEW_PERMISSION);
        SiteMapConfiguration map = store.findMap(context.tenantId(), siteMapId)
                .orElseThrow(() -> new GisException(GisErrorCode.GIS_TENANT_001, "地图不属于当前租户"));
        List<MapPointProjection> projections = new ArrayList<>();
        for (MapPointConfiguration point : store.findPoints(context.tenantId(), map.id())) {
            if (entityType != null && entityType != point.entityType()) {
                continue;
            }
            try {
                MapPointProjection projection = projectPoint(context, point);
                if (projection != null && (status == null || status == projection.displayStatus())) {
                    projections.add(projection);
                }
            } catch (GisException exception) {
                // 地图投影是配置集合：单个已删除/不可用源点不能阻断其他合法点位。
                if (exception.getBusinessCode().equals(GisErrorCode.GIS_POINT_001.businessCode())
                        || exception.getBusinessCode().equals(GisErrorCode.GIS_TENANT_001.businessCode())) {
                    continue;
                }
                throw exception;
            }
        }
        return new SiteMapProjection(map.id(), map.mapCode(), map.mapName(), map.asset().mimeType(),
                map.asset().storageKey(), projections, clock.instant(), context.requestId());
    }

    /** 查询单个点位；不存在、跨租户和无权访问统一隐藏。 */
    public MapPointProjection getPoint(FactsQueryContext context, UUID pointId) {
        requirePermission(context, MAP_VIEW_PERMISSION);
        MapPointConfiguration point = store.findPoint(context.tenantId(), pointId)
                .orElseThrow(() -> new GisException(GisErrorCode.GIS_POINT_001, null));
        MapPointProjection projection = projectPoint(context, point);
        if (projection == null) {
            throw new GisException(GisErrorCode.GIS_POINT_001, null);
        }
        return projection;
    }

    private MapPointProjection projectPoint(FactsQueryContext context, MapPointConfiguration point) {
        ReferencedEntity entity = resolveEntity(context, point.entityType(), point.entityId());
        if (!entity.visible() || !context.tenantId().equals(entity.tenantId())) {
            return null;
        }
        DisplayStatus displayStatus = statusOf(context, point, entity);
        PointStatusFacts deviceFacts = point.entityType() == MapEntityType.DEVICE
                ? iotFacts.pointStatus(context, point.entityId()).orElse(null) : null;
        return new MapPointProjection(point.id(), point.entityType(), point.entityId(), entity.displayName(),
                point.xPercent(), point.yPercent(), point.rotation(), displayStatus, point.linkedPage(),
                deviceFacts == null ? entity.sourceUpdatedAt() : deviceFacts.sourceUpdatedAt(),
                deviceFacts == null ? null : deviceFacts.alarmId(),
                deviceFacts == null ? null : deviceFacts.alarmLevel(),
                deviceFacts == null ? null : deviceFacts.alarmStatus(),
                deviceFacts == null ? null : deviceFacts.occurredAt());
    }

    private DisplayStatus statusOf(FactsQueryContext context, MapPointConfiguration point,
                                   ReferencedEntity entity) {
        if (point.entityType() != MapEntityType.DEVICE) {
            return parseDisplayStatus(entity.displayStatus());
        }
        PointStatusFacts facts;
        try {
            facts = iotFacts.pointStatus(context, point.entityId()).orElse(null);
        } catch (FactQueryUnavailableException exception) {
            throw new GisException(GisErrorCode.GIS_QUERY_002, "设备状态源暂时不可用");
        }
        if (facts == null) {
            return parseDisplayStatus(entity.displayStatus());
        }
        return DisplayStatus.valueOf(DisplayStatus.highest(facts.alarm(), facts.offline(), facts.warning()).name());
    }

    private ReferencedEntity resolveEntity(FactsQueryContext context, MapEntityType type, UUID id) {
        try {
            return (switch (type) {
                case WAREHOUSE -> inventoryFacts.findWarehouse(context, id);
                case PRODUCTION_AREA -> manufacturingFacts.findProductionArea(context, id);
                case DEVICE -> iotFacts.findDevice(context, id);
            }).orElseThrow(() -> new GisException(GisErrorCode.GIS_POINT_001, "点位源实体不可用"));
        } catch (FactQueryUnavailableException exception) {
            throw new GisException(GisErrorCode.GIS_QUERY_002, "点位源查询暂时不可用");
        }
    }

    private static DisplayStatus parseDisplayStatus(String status) {
        if (status == null) {
            return DisplayStatus.NORMAL;
        }
        try {
            return DisplayStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DisplayStatus.NORMAL;
        }
    }

    private static void requirePermission(FactsQueryContext context, String permission) {
        if (context == null || !context.hasPermission(permission)) {
            throw new GisException(GisErrorCode.GIS_AUTH_001, null);
        }
    }

    private static void validatePointCommand(SaveMapPointCommand command) {
        if (command == null || command.siteMapId() == null || command.entityType() == null
                || command.entityId() == null || !Double.isFinite(command.xPercent())
                || !Double.isFinite(command.yPercent()) || !Double.isFinite(command.rotation())) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "点位字段不完整");
        }
        if (command.xPercent() < 0 || command.xPercent() > 100
                || command.yPercent() < 0 || command.yPercent() > 100) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "点位坐标必须在 0 至 100 百分比之间");
        }
        if (command.rotation() < -360 || command.rotation() > 360) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "点位 rotation 必须在 -360 至 360 度之间");
        }
    }

    private static String digest(SaveMapPointCommand command) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(command.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    private record IdempotentPoint(String digest, MapPointConfiguration point) {
    }
}
