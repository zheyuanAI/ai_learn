package com.ailearn.platform.core.dashboard.controller;

import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 七类综合看板只读 REST API。
 * <p>
 * Controller 只接受受控筛选条件，租户和权限始终来自可信上下文；指标必须来自真实 Facts，
 * 源事实不可用时由应用服务返回陈旧投影或明确 503。
 * </p>
 */
@RestController
@RequestMapping("/api/dashboard")
@ConditionalOnBean(DashboardApplicationService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DashboardController {

    private final DashboardApplicationService applicationService;
    private final TrustedFactsQueryContextFactory contextFactory;

    /**
     * 注入看板应用服务和可信上下文工厂。
     *
     * @param applicationService 看板应用服务
     * @param contextFactory 认证上下文工厂
     */
    public DashboardController(DashboardApplicationService applicationService,
                               TrustedFactsQueryContextFactory contextFactory) {
        this.applicationService = applicationService;
        this.contextFactory = contextFactory;
    }

    /** 查询库存事实摘要。 */
    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:inventory:view')")
    public ApiResponse<DashboardSummaryProjection> inventory(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.INVENTORY, parameters);
    }

    /** 查询采购与销售履约事实摘要。 */
    @GetMapping("/fulfillment")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:fulfillment:view')")
    public ApiResponse<DashboardSummaryProjection> fulfillment(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.FULFILLMENT, parameters);
    }

    /** 查询制造事实摘要。 */
    @GetMapping("/manufacturing")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:manufacturing:view')")
    public ApiResponse<DashboardSummaryProjection> manufacturing(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.MANUFACTURING, parameters);
    }

    /** 查询质量事实摘要。 */
    @GetMapping("/quality")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:quality:view')")
    public ApiResponse<DashboardSummaryProjection> quality(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.QUALITY, parameters);
    }

    /** 查询设备事实摘要。 */
    @GetMapping("/device")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:device:view')")
    public ApiResponse<DashboardSummaryProjection> device(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.DEVICE, parameters);
    }

    /** 查询告警事实摘要。 */
    @GetMapping("/alarms")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:alarm:view')")
    public ApiResponse<DashboardSummaryProjection> alarms(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.ALARM, parameters);
    }

    /** 查询追溯事实摘要。 */
    @GetMapping("/traceability")
    @PreAuthorize("hasAuthority('dashboard:view') or hasAuthority('dashboard:traceability:view')")
    public ApiResponse<DashboardSummaryProjection> traceability(
            @RequestParam Map<String, String> parameters) {
        return query(DashboardSummaryType.TRACEABILITY, parameters);
    }

    private ApiResponse<DashboardSummaryProjection> query(DashboardSummaryType type,
                                                           Map<String, String> parameters) {
        Map<String, String> safeParameters = parameters == null ? Map.of() : parameters;
        DashboardQuery query = new DashboardQuery(contextFactory.current(),
                firstText(safeParameters.get("time_range"), safeParameters.get("timeRange")),
                filters(safeParameters));
        return ApiResponse.success(applicationService.query(type, query));
    }

    /** 只提取白名单筛选字段；客户端传入的 tenant_id 等未知参数不会传给 Facts 端口。 */
    private static Map<String, String> filters(Map<String, String> parameters) {
        Map<String, String> filters = new LinkedHashMap<>();
        put(filters, "warehouse_id", firstText(parameters.get("warehouse_id"), parameters.get("warehouseId")));
        put(filters, "production_area_id",
                firstText(parameters.get("production_area_id"), parameters.get("areaId")));
        put(filters, "device_id", firstText(parameters.get("device_id"), parameters.get("deviceId")));
        return filters;
    }

    private static void put(Map<String, String> filters, String key, String value) {
        if (value != null && !value.isBlank()) {
            filters.put(key, value.trim());
        }

    }

    private static String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
