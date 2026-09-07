package com.ailearn.platform.core.gis.controller;

import com.ailearn.platform.core.gis.application.GisApplicationService;
import com.ailearn.platform.core.gis.domain.DisplayStatus;
import com.ailearn.platform.core.gis.domain.MapEntityType;
import com.ailearn.platform.core.gis.dto.CreateSiteMapCommand;
import com.ailearn.platform.core.gis.dto.MapPointProjection;
import com.ailearn.platform.core.gis.dto.SaveMapPointCommand;
import com.ailearn.platform.core.gis.dto.SiteMapProjection;
import com.ailearn.platform.core.gis.domain.MapPointConfiguration;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 二维 GIS 配置与投影 REST API。
 * <p>
 * 认证上下文由共享安全过滤器建立；Controller 不读取客户端 tenant_id，也不直接访问任何源领域表。
 * </p>
 */
@RestController
// S7 控制器由同一开关统一启用，避免组件扫描早于条件 Bean 注册造成启动顺序依赖。
@ConditionalOnProperty(prefix = "core.facts.iot", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GisController {

    private final GisApplicationService applicationService;
    private final TrustedFactsQueryContextFactory contextFactory;

    /**
     * 注入 GIS 应用服务和可信上下文工厂。
     *
     * @param applicationService GIS 应用服务
     * @param contextFactory 认证上下文工厂
     */
    public GisController(GisApplicationService applicationService,
                         TrustedFactsQueryContextFactory contextFactory) {
        this.applicationService = applicationService;
        this.contextFactory = contextFactory;
    }

    /** 查询当前租户地图列表。 */
    @GetMapping("/api/site-maps")
    @PreAuthorize("hasAuthority('gis:map:view')")
    public ApiResponse<List<SiteMapConfiguration>> listMaps() {
        return ApiResponse.success(applicationService.listMaps(contextFactory.current()));
    }

    /** 创建当前租户地图，只保存 GIS 自有配置；重复请求按 Idempotency-Key 重放。 */
    @PostMapping("/api/site-maps")
    @PreAuthorize("hasAuthority('gis:map:manage')")
    public ApiResponse<SiteMapConfiguration> createMap(
            @RequestBody CreateSiteMapCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.createMap(contextFactory.current(), command, idempotencyKey));
    }

    /** 查询指定地图投影；site_map_id 只能标识当前租户内资源。 */
    @GetMapping("/api/site-map")
    @PreAuthorize("hasAuthority('gis:map:view')")
    public ApiResponse<SiteMapProjection> projection(
            @RequestParam(name = "site_map_id", required = false) UUID siteMapId,
            @RequestParam(name = "entity_type", required = false) MapEntityType entityType,
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.success(applicationService.getSiteMap(contextFactory.current(),
                requireSiteMapId(siteMapId), entityType, parseStatus(status)));
    }

    /** 与前端地图编辑器约定的指定地图投影路径。 */
    @GetMapping("/api/site-maps/{siteMapId}/projection")
    @PreAuthorize("hasAuthority('gis:map:view')")
    public ApiResponse<SiteMapProjection> projectionByMap(
            @PathVariable("siteMapId") UUID siteMapId,
            @RequestParam(name = "entity_type", required = false) MapEntityType entityType,
            @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.success(applicationService.getSiteMap(contextFactory.current(), siteMapId,
                entityType, parseStatus(status)));
    }

    /** 查询当前用户可见的单个点位。 */
    @GetMapping("/api/site-map/points/{pointId}")
    @PreAuthorize("hasAuthority('gis:map:view')")
    public ApiResponse<MapPointProjection> point(@PathVariable("pointId") UUID pointId) {
        return ApiResponse.success(applicationService.getPoint(contextFactory.current(), pointId));
    }

    /** 保存点位配置；点位幂等键在 HTTP 头中传入，不接受客户端租户字段。 */
    @PostMapping("/api/site-map/points")
    @PreAuthorize("hasAuthority('gis:map:manage')")
    public ApiResponse<MapPointConfiguration> savePoint(
            @RequestBody SaveMapPointCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.savePoint(contextFactory.current(), command,
                idempotencyKey));
    }

    /** 更新当前租户点位配置；点位标识来自路径，不能由请求体替换。 */
    @PutMapping("/api/site-map/points/{pointId}")
    @PreAuthorize("hasAuthority('gis:map:manage')")
    public ApiResponse<MapPointConfiguration> updatePoint(
            @PathVariable("pointId") UUID pointId,
            @RequestBody SaveMapPointCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.updatePoint(contextFactory.current(), pointId, command,
                idempotencyKey));
    }

    /** 软删除当前租户点位配置；删除只影响 GIS 自有配置。 */
    @DeleteMapping("/api/site-map/points/{pointId}")
    @PreAuthorize("hasAuthority('gis:map:manage')")
    public ApiResponse<Boolean> deletePoint(@PathVariable("pointId") UUID pointId,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.deletePoint(contextFactory.current(), pointId,
                idempotencyKey));
    }

    private static UUID requireSiteMapId(UUID siteMapId) {
        if (siteMapId == null) {
            throw new GisException(GisErrorCode.GIS_QUERY_001, "site_map_id 不能为空");
        }
        return siteMapId;
    }

    private static DisplayStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DisplayStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new GisException(GisErrorCode.GIS_QUERY_001, "不支持的地图状态: " + value);
        }
    }
}
