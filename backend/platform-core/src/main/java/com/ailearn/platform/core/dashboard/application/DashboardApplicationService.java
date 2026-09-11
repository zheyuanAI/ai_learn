package com.ailearn.platform.core.dashboard.application;

import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.domain.DashboardTimeRange;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import com.ailearn.platform.core.dashboard.ports.DashboardCache;
import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.ports.FactQueryUnavailableException;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.PurchasingFactsQuery;
import com.ailearn.platform.core.traceability.ports.QualityFactsQuery;
import com.ailearn.platform.core.traceability.ports.SalesFactsQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 七类综合看板查询应用服务。
 * <p>
 * 每类摘要都通过上游 Facts 端口产生；本类只负责权限、时间范围、聚合和可测试内存缓存。
 * </p>
 */
public class DashboardApplicationService {
    private static final Duration FRESH_TTL = Duration.ofSeconds(60);
    private static final Duration STALE_TTL = Duration.ofMinutes(10);

    private final InventoryFactsQuery inventoryFacts;
    private final PurchasingFactsQuery purchasingFacts;
    private final SalesFactsQuery salesFacts;
    private final ManufacturingFactsQuery manufacturingFacts;
    private final QualityFactsQuery qualityFacts;
    private final IotFactsPort iotFacts;
    private final TraceabilityApplicationService traceability;
    private final DashboardCache cache;
    private final Clock clock;

    public DashboardApplicationService(InventoryFactsQuery inventoryFacts,
                                       PurchasingFactsQuery purchasingFacts,
                                       SalesFactsQuery salesFacts,
                                       ManufacturingFactsQuery manufacturingFacts,
                                       QualityFactsQuery qualityFacts,
                                       IotFactsPort iotFacts,
                                       TraceabilityApplicationService traceability,
                                       DashboardCache cache, Clock clock) {
        this.inventoryFacts = inventoryFacts;
        this.purchasingFacts = purchasingFacts;
        this.salesFacts = salesFacts;
        this.manufacturingFacts = manufacturingFacts;
        this.qualityFacts = qualityFacts;
        this.iotFacts = iotFacts;
        this.traceability = traceability;
        this.cache = cache;
        this.clock = clock;
    }

    /**
     * 查询指定摘要。
     * 入参：可信上下文、摘要类型和时间/筛选条件；出参：新鲜或明确陈旧的摘要；流程：权限与筛选校验、读取新鲜缓存、调用 Facts、失败时回退 10 分钟缓存。
     */
    public DashboardSummaryProjection query(DashboardSummaryType type, DashboardQuery query) {
        requirePermission(query.context(), type);
        DashboardTimeRange timeRange = DashboardTimeRange.parse(query.timeRange(),
                query.context().tenantZone(), clock.instant());
        validateFilters(query.context(), query.filters());
        FactsQueryRequest factsRequest = new FactsQueryRequest(query.context(), timeRange.from(),
                timeRange.to(), query.filters());
        String cacheKey = cacheKey(type, query.context(), timeRange, query.filters());
        Instant now = clock.instant();
        DashboardSummaryProjection cached = cache.find(cacheKey).orElse(null);
        if (cached != null && cached.generatedAt() != null
                && !cached.generatedAt().plus(FRESH_TTL).isBefore(now)) {
            return withRequestId(cached, query.context().requestId());
        }
        try {
            FactsSummary facts = load(type, factsRequest);
            DashboardSummaryProjection fresh = new DashboardSummaryProjection(type, facts.metrics(), timeRange,
                    facts.sourceSummary(), now, facts.sourceUpdatedAt(), false, null, query.context().requestId());
            cache.save(cacheKey, fresh, now);
            return fresh;
        } catch (FactQueryUnavailableException exception) {
            if (cached != null && cached.generatedAt() != null
                    && !cached.generatedAt().plus(STALE_TTL).isBefore(now)) {
                return new DashboardSummaryProjection(cached.summaryType(), cached.metrics(), cached.timeRange(),
                        cached.sourceSummary(), cached.generatedAt(), cached.sourceUpdatedAt(), true,
                        cached.generatedAt(), query.context().requestId());
            }
            throw new GisException(GisErrorCode.GIS_QUERY_002, "源领域查询暂时不可用");
        }
    }

    /** 通过字符串路由供最小 HTTP 适配器调用。 */
    public DashboardSummaryProjection query(String summaryType, DashboardQuery query) {
        return query(DashboardSummaryType.parse(summaryType), query);
    }

    /**
     * 按摘要类型只检查并调用该接口自己的事实端口；履约摘要需要同时合并采购和销售来源。
     * 缺少某一端口时仅该摘要进入陈旧回退或返回 GIS_QUERY_002，不影响其他看板接口。
     */
    private FactsSummary load(DashboardSummaryType type, FactsQueryRequest request) {
        try {
            return switch (type) {
                case INVENTORY -> requireSource(inventoryFacts, "库存").inventory(request);
                case FULFILLMENT -> merge("采购",
                        requireSource(purchasingFacts, "采购履约").fulfillment(request),
                        "销售", requireSource(salesFacts, "销售履约").fulfillment(request));
                case MANUFACTURING -> requireSource(manufacturingFacts, "制造").manufacturing(request);
                case QUALITY -> requireSource(qualityFacts, "质量").quality(request);
                case DEVICE -> requireSource(iotFacts, "设备").device(request);
                case ALARM -> requireSource(iotFacts, "告警").alarm(request);
                case TRACEABILITY -> requireSource(traceability, "追溯").summary(request);
            };
        } catch (FactQueryUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FactQueryUnavailableException("摘要源查询失败", exception);
        }
    }

    /** 返回当前摘要需要的事实端口；端口未装配时转换为统一的事实源不可用。 */
    private static <T> T requireSource(T source, String sourceName) {
        if (source == null) {
            throw new FactQueryUnavailableException(sourceName + "事实源未装配");
        }
        return source;
    }

    /**
     * 合并采购与销售履约指标，不产生新的业务事实；同名指标相加，来源更新时间取较新者，便于响应标明聚合口径。
     */
    private static FactsSummary merge(String firstName, FactsSummary first,
                                      String secondName, FactsSummary second) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>(first.metrics());
        second.metrics().forEach((key, value) -> metrics.merge(key, value, BigDecimal::add));
        String summary = List.of(firstName + ": " + first.sourceSummary(), secondName + ": " + second.sourceSummary())
                .stream().filter(item -> !item.endsWith(": ")).reduce((left, right) -> left + ", " + right).orElse("");
        Instant updated = first.sourceUpdatedAt();
        if (updated == null || (second.sourceUpdatedAt() != null && second.sourceUpdatedAt().isAfter(updated))) {
            updated = second.sourceUpdatedAt();
        }
        return new FactsSummary(metrics, summary, updated);
    }

    /**
     * 只校验契约允许的实体筛选项，并把可信查询上下文传递给各领域 Facts 端口，避免看板自行拼接跨域查询。
     */
    private void validateFilters(FactsQueryContext context, Map<String, String> filters) {
        validateEntityFilter(filters, "warehouse_id",
                value -> requireSource(inventoryFacts, "库存").findWarehouse(context, value));
        validateEntityFilter(filters, "production_area_id",
                value -> requireSource(manufacturingFacts, "制造").findProductionArea(context, value));
        validateEntityFilter(filters, "device_id",
                value -> requireSource(iotFacts, "设备").findDevice(context, value));
    }

    /**
     * 校验白名单实体筛选，并把格式错误、实体不存在或不属于当前租户统一收敛为 GIS_TENANT_001，避免泄露跨租户实体是否存在。
     * 具体查找仍通过带有可信上下文的 Facts 端口完成，不能仅凭前端传入的 UUID 判定可见性。
     */
    private static void validateEntityFilter(Map<String, String> filters, String key,
                                             EntityLookup lookup) {
        String raw = filters.get(key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw new GisException(GisErrorCode.GIS_TENANT_001, "筛选实体不可用");
        }
        try {
            if (lookup.find(id).isEmpty()) {
                throw new GisException(GisErrorCode.GIS_TENANT_001, "筛选实体不可用");
            }
        } catch (FactQueryUnavailableException exception) {
            throw new GisException(GisErrorCode.GIS_QUERY_002, "筛选实体查询暂时不可用");
        }
    }

    /**
     * 生成与请求内容无关的稳定缓存键：租户和权限指纹隔离数据范围，排序后的筛选条件保证参数顺序不影响命中。
     * request_id 不进入缓存键，命中后由 withRequestId 使用当前请求号重新包装响应。
     */
    private static String cacheKey(DashboardSummaryType type, FactsQueryContext context,
                                   DashboardTimeRange range, Map<String, String> filters) {
        String normalizedFilters = filters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right).orElse("");
        return context.tenantId() + "|" + context.permissionFingerprint() + "|" + type.key()
                + "|" + range.key() + "|" + normalizedFilters;
    }

    /** 命中共享缓存时保留当前请求号，避免把前一请求的 request_id 泄漏到本次响应。 */
    private static DashboardSummaryProjection withRequestId(DashboardSummaryProjection cached,
                                                             String requestId) {
        return new DashboardSummaryProjection(cached.summaryType(), cached.metrics(), cached.timeRange(),
                cached.sourceSummary(), cached.generatedAt(), cached.sourceUpdatedAt(), cached.stale(),
                cached.staleSince(), requestId);
    }

    /**
     * 校验看板总权限或摘要专属权限；缺少可信上下文时直接按无权访问处理，不依赖前端菜单可见性作为安全判断。
     */
    private static void requirePermission(FactsQueryContext context, DashboardSummaryType type) {
        if (context == null || (!context.hasPermission("dashboard:view")
                && !context.hasPermission(type.permission()))) {
            throw new GisException(GisErrorCode.GIS_AUTH_001, "当前用户无看板查询权限");
        }
    }

    @FunctionalInterface
    private interface EntityLookup {
        /** 在已绑定租户和权限的 Facts 查询范围内查找筛选实体。 */
        java.util.Optional<com.ailearn.platform.core.traceability.ports.ReferencedEntity> find(UUID id);
    }
}
