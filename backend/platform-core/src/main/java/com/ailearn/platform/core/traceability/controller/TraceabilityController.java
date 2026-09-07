package com.ailearn.platform.core.traceability.controller;

import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.dto.TraceabilityProjection;
import com.ailearn.platform.core.traceability.dto.TraceabilityQuery;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨域追溯只读 REST API。
 * <p>
 * 入口实体必须使用已存在事实的 UUID；本 Controller 不把业务编码猜测成 UUID，也不创建关系事实。
 * </p>
 */
@RestController
// S7 控制器由同一开关统一启用，避免组件扫描早于条件 Bean 注册造成启动顺序依赖。
@ConditionalOnProperty(prefix = "core.facts.iot", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceabilityController {

    private final TraceabilityApplicationService applicationService;
    private final TrustedFactsQueryContextFactory contextFactory;

    /**
     * 注入追溯应用服务和可信上下文工厂。
     *
     * @param applicationService 追溯应用服务
     * @param contextFactory 认证上下文工厂
     */
    public TraceabilityController(TraceabilityApplicationService applicationService,
                                  TrustedFactsQueryContextFactory contextFactory) {
        this.applicationService = applicationService;
        this.contextFactory = contextFactory;
    }

    /**
     * 查询跨域追溯链。
     * 入参：正式参数 entity_type 与 entity_id；出参：真实 Facts 节点、关系和缺失来源标记。
     */
    @GetMapping("/api/traceability")
    @PreAuthorize("hasAuthority('trace:chain:view') or hasAuthority('ai:trace:view')")
    public ApiResponse<TraceabilityProjection> query(
            @RequestParam(name = "entity_type", required = false) String entityType,
            @RequestParam(name = "entity_id", required = false) UUID entityId) {
        if (entityType == null || entityType.isBlank() || entityId == null) {
            throw new GisException(GisErrorCode.GIS_QUERY_001, "追溯入口必须提供 entity_type 和 entity_id");
        }
        return ApiResponse.success(applicationService.query(
                new TraceabilityQuery(contextFactory.current(), entityType.trim(), entityId)));
    }
}
