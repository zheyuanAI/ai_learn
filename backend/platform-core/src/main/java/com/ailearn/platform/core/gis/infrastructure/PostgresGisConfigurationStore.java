package com.ailearn.platform.core.gis.infrastructure;

import com.ailearn.platform.core.gis.domain.MapAssetMetadata;
import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.gis.ports.GisConfigurationStore;
import com.ailearn.platform.core.gis.ports.MapPointIdempotencyRecord;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * GIS 自有展示配置的 PostgreSQL 适配器。
 * <p>
 * 只访问 Core V6 的四张 GIS 表；每条 SQL 都显式带 {@code tenant_id}，并通过地图/点位的复合外键
 * 保证租户不能串写。源实体状态仍由 Facts 端口读取，本适配器不访问源领域表。
 * </p>
 */
@Repository
public class PostgresGisConfigurationStore implements GisConfigurationStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresGisConfigurationStore.class);

    private static final String MAP_SELECT = """
            SELECT m.id, m.tenant_id, m.map_code, m.map_name, m.background_type,
                   m.created_at, m.updated_at, a.storage_key, a.mime_type, a.size_bytes, a.sha256
              FROM gis_site_map m
              JOIN gis_site_map_asset a
                ON a.tenant_id = m.tenant_id AND a.site_map_id = m.id AND a.isdel = 0
             WHERE m.tenant_id = ? AND m.id = ? AND m.isdel = 0
            """;

    private static final String MAP_LIST = """
            SELECT m.id, m.tenant_id, m.map_code, m.map_name, m.background_type,
                   m.created_at, m.updated_at, a.storage_key, a.mime_type, a.size_bytes, a.sha256
              FROM gis_site_map m
              JOIN gis_site_map_asset a
                ON a.tenant_id = m.tenant_id AND a.site_map_id = m.id AND a.isdel = 0
             WHERE m.tenant_id = ? AND m.isdel = 0
             ORDER BY m.map_code, m.id
            """;

    private static final String POINT_SELECT = """
            SELECT id, tenant_id, site_map_id, entity_type, entity_id,
                   x_percent, y_percent, rotation, linked_page, created_at, updated_at,
                   idempotency_key, payload_digest
              FROM gis_map_point
             WHERE tenant_id = ? AND id = ? AND isdel = 0
            """;

    private static final String POINT_BY_IDEMPOTENCY = """
            SELECT id, tenant_id, site_map_id, entity_type, entity_id,
                   x_percent, y_percent, rotation, linked_page, created_at, updated_at,
                   idempotency_key, payload_digest
              FROM gis_map_point
             WHERE tenant_id = ? AND idempotency_key = ? AND isdel = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 GIS PostgreSQL 适配器。
     *
     * @param jdbcTemplate Core 数据库访问模板
     */
    public PostgresGisConfigurationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 在同一事务中保存地图表头和底图元数据。
     * 入参：带可信租户的地图配置；出参：已保存配置；流程：写入地图表，再写入同租户底图资产表。
     *
     * @param map 地图配置
     * @return 已保存地图
     */
    @Override
    @Transactional
    public SiteMapConfiguration saveMap(SiteMapConfiguration map) {
        try {
            int mapRows = jdbcTemplate.update("""
                    INSERT INTO gis_site_map
                        (id, tenant_id, map_code, map_name, background_type,
                         isdel, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?)
            """, map.id(), map.tenantId(), map.mapCode(), map.mapName(),
                    backgroundType(map.asset()), jdbcTime(map.createdAt()), jdbcTime(map.updatedAt()));
            if (mapRows != 1) {
                throw new ServiceUnavailableException("GIS 地图配置写入失败");
            }
            int assetRows = jdbcTemplate.update("""
                    INSERT INTO gis_site_map_asset
                        (id, tenant_id, site_map_id, storage_key, mime_type,
                         size_bytes, sha256, isdel, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """, UUID.randomUUID(), map.tenantId(), map.id(), map.asset().storageKey(),
                    map.asset().mimeType(), map.asset().sizeBytes(), map.asset().sha256(),
                    jdbcTime(map.createdAt()), jdbcTime(map.updatedAt()));
            if (assetRows != 1) {
                throw new ServiceUnavailableException("GIS 底图元数据写入失败");
            }
            return map;
        } catch (DuplicateKeyException exception) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "当前租户地图编码已存在");
        } catch (DataAccessException exception) {
            // 修改用途：记录地图或底图写入的数据库根因，便于本地练习环境定位 schema/连接问题。
            LOGGER.warn("GIS 地图配置写入失败，已转换为统一服务不可用响应", exception);
            throw unavailable("GIS 地图配置数据库暂时不可用", exception);
        }
    }

    /**
     * 兼容不带显式幂等元数据的旧端口调用；新生产调用必须使用带幂等键的重载。
     *
     * @param point 点位配置
     * @return 已保存点位
     */
    @Override
    public MapPointConfiguration savePoint(MapPointConfiguration point) {
        return savePoint(point, point.id().toString(), digest(point.toString()));
    }

    /**
     * 保存点位并由数据库唯一索引承接跨实例幂等。
     * 入参：点位、当前租户幂等键和服务端载荷摘要；出参：首次写入或已存在的同载荷点位；流程：
     * 按租户竞争唯一键，发生重放时读取数据库记录并校验摘要。
     *
     * @param point 点位配置
     * @param idempotencyKey 当前租户幂等键
     * @param payloadDigest 服务端载荷摘要
     * @return 已保存点位
     */
    @Override
    public MapPointConfiguration savePoint(MapPointConfiguration point, String idempotencyKey,
                                           String payloadDigest) {
        String key = requireText(idempotencyKey, "点位幂等键不能为空");
        String digest = normalizeDigest(payloadDigest);
        try {
            int rows = jdbcTemplate.update("""
                    INSERT INTO gis_map_point
                        (id, tenant_id, site_map_id, entity_type, entity_id,
                         x_percent, y_percent, rotation, linked_page,
                         idempotency_key, payload_digest, isdel, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    ON CONFLICT (tenant_id, idempotency_key) WHERE isdel = 0 DO NOTHING
                    """, point.id(), point.tenantId(), point.siteMapId(), point.entityType().name(),
                    point.entityId(), point.xPercent(), point.yPercent(), point.rotation(),
                    point.linkedPage(), key, digest, jdbcTime(point.createdAt()), jdbcTime(point.updatedAt()));
            if (rows == 1) {
                return point;
            }
            MapPointIdempotencyRecord existing = findPointByIdempotencyKey(point.tenantId(), key)
                    .orElseThrow(() -> new ServiceUnavailableException("GIS 点位幂等记录写入后无法读取"));
            if (!existing.payloadDigest().equals(digest)) {
                throw new GisException(GisErrorCode.GIS_POINT_002, "同一幂等键的点位载荷不一致");
            }
            return existing.point();
        } catch (DuplicateKeyException exception) {
            MapPointIdempotencyRecord existing = findPointByIdempotencyKey(point.tenantId(), key)
                    .orElseThrow(() -> unavailable("GIS 点位幂等竞争后无法读取记录", exception));
            if (!existing.payloadDigest().equals(digest)) {
                throw new GisException(GisErrorCode.GIS_POINT_002, "同一幂等键的点位载荷不一致");
            }
            return existing.point();
        } catch (DataAccessException exception) {
            throw unavailable("GIS 点位配置数据库暂时不可用", exception);
        }
    }

    /** 查询当前租户的单张未删除地图及底图元数据。 */
    @Override
    public Optional<SiteMapConfiguration> findMap(UUID tenantId, UUID mapId) {
        return database(() -> jdbcTemplate.query(MAP_SELECT, this::mapSiteMap, tenantId, mapId)
                .stream().findFirst());
    }

    /** 查询当前租户的未删除地图及底图元数据。 */
    @Override
    public List<SiteMapConfiguration> findMaps(UUID tenantId) {
        return database(() -> jdbcTemplate.query(MAP_LIST, this::mapSiteMap, tenantId));
    }

    /** 查询当前租户指定地图的未删除点位。 */
    @Override
    public List<MapPointConfiguration> findPoints(UUID tenantId, UUID mapId) {
        return database(() -> jdbcTemplate.query("""
                SELECT id, tenant_id, site_map_id, entity_type, entity_id,
                       x_percent, y_percent, rotation, linked_page, created_at, updated_at
                  FROM gis_map_point
                 WHERE tenant_id = ? AND site_map_id = ? AND isdel = 0
                 ORDER BY created_at, id
                """, this::mapPoint, tenantId, mapId));
    }

    /** 查询当前租户的单个未删除点位。 */
    @Override
    public Optional<MapPointConfiguration> findPoint(UUID tenantId, UUID pointId) {
        return database(() -> jdbcTemplate.query(POINT_SELECT, this::mapPoint, tenantId, pointId)
                .stream().findFirst());
    }

    /** 查询当前租户的持久化点位幂等记录。 */
    @Override
    public Optional<MapPointIdempotencyRecord> findPointByIdempotencyKey(UUID tenantId,
                                                                          String idempotencyKey) {
        String key = requireText(idempotencyKey, "点位幂等键不能为空");
        return database(() -> jdbcTemplate.query(POINT_BY_IDEMPOTENCY, this::mapIdempotencyRecord,
                tenantId, key).stream().findFirst());
    }

    /** 更新当前租户点位配置；地图与实体租户约束由应用层和数据库复合外键共同保证。 */
    @Override
    public MapPointConfiguration updatePoint(UUID tenantId, UUID pointId, MapPointConfiguration point) {
        try {
            int rows = jdbcTemplate.update("""
                    UPDATE gis_map_point
                       SET site_map_id = ?, entity_type = ?, entity_id = ?, x_percent = ?,
                           y_percent = ?, rotation = ?, linked_page = ?, updated_at = ?
                    WHERE tenant_id = ? AND id = ? AND isdel = 0
                    """, point.siteMapId(), point.entityType().name(), point.entityId(), point.xPercent(),
                    point.yPercent(), point.rotation(), point.linkedPage(), jdbcTime(point.updatedAt()), tenantId, pointId);
            if (rows != 1) {
                throw new GisException(GisErrorCode.GIS_POINT_001, null);
            }
            return point;
        } catch (DataAccessException exception) {
            throw unavailable("GIS 点位更新数据库暂时不可用", exception);
        }
    }

    /** 在当前租户范围内软删除点位，保留历史配置行以便审计。 */
    @Override
    public boolean deletePoint(UUID tenantId, UUID pointId) {
        try {
            return jdbcTemplate.update("""
                    UPDATE gis_map_point
                       SET isdel = 1, updated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND id = ? AND isdel = 0
                    """, tenantId, pointId) == 1;
        } catch (DataAccessException exception) {
            throw unavailable("GIS 点位删除数据库暂时不可用", exception);
        }
    }

    private MapPointIdempotencyRecord mapIdempotencyRecord(ResultSet resultSet, int rowNum)
            throws SQLException {
        return new MapPointIdempotencyRecord(mapPoint(resultSet, rowNum),
                resultSet.getString("payload_digest"));
    }

    private SiteMapConfiguration mapSiteMap(ResultSet resultSet, int rowNum) throws SQLException {
        String mimeType = resultSet.getString("mime_type");
        MapAssetMetadata asset = new MapAssetMetadata(resultSet.getString("storage_key"), mimeType,
                resultSet.getLong("size_bytes"), resultSet.getString("sha256"));
        return new SiteMapConfiguration(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getString("map_code"),
                resultSet.getString("map_name"), asset,
                instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private MapPointConfiguration mapPoint(ResultSet resultSet, int rowNum) throws SQLException {
        return new MapPointConfiguration(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("site_map_id", UUID.class),
                com.ailearn.platform.core.gis.domain.MapEntityType.valueOf(
                        resultSet.getString("entity_type")),
                resultSet.getObject("entity_id", UUID.class),
                resultSet.getBigDecimal("x_percent").doubleValue(),
                resultSet.getBigDecimal("y_percent").doubleValue(),
                resultSet.getBigDecimal("rotation").doubleValue(),
                resultSet.getString("linked_page"),
                instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
    }

    private static java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String backgroundType(MapAssetMetadata asset) {
        return switch (asset.mimeType()) {
            case "image/png" -> "PNG";
            case "image/jpeg" -> "JPEG";
            case "image/webp" -> "WEBP";
            default -> throw new GisException(GisErrorCode.GIS_CONFIG_001, "底图格式不支持");
        };
    }

    /** 将领域层 Instant 显式转换为 JDBC 可识别的带时区时间，兼容 PostgreSQL 12.1 驱动。 */
    private static OffsetDateTime jdbcTime(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, message);
        }
        return value.trim();
    }

    private static String normalizeDigest(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new GisException(GisErrorCode.GIS_CONFIG_001, "点位 payloadDigest 格式不合法");
        }
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    private <T> T database(Supplier<T> action) {
        try {
            return action.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            // 修改用途：保留数据库根因供本地练习环境排查，同时对外仍只返回统一的 503 业务错误。
            LOGGER.warn("GIS 配置数据库访问失败，已转换为统一服务不可用响应", exception);
            throw unavailable("GIS 配置数据库暂时不可用", exception);
        }
    }

    private static ServiceUnavailableException unavailable(String message, Throwable cause) {
        return new ServiceUnavailableException(message, cause);
    }
}
