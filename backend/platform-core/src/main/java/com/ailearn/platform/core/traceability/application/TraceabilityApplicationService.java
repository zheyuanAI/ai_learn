package com.ailearn.platform.core.traceability.application;

import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.dto.TraceabilityProjection;
import com.ailearn.platform.core.traceability.dto.TraceabilityQuery;
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
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨域追溯查询应用服务。
 * <p>
 * 各领域事实端口返回真实来源节点和关系，本服务只负责合并、权限裁剪和完整性标记。
 * </p>
 */
public class TraceabilityApplicationService {
    public static final String TRACE_VIEW_PERMISSION = "trace:chain:view";
    /** V2/V5 已存在的菜单权限；保留读取兼容，避免历史登录用户在 V6 目录上线后被无故拒绝。 */
    public static final String LEGACY_TRACE_VIEW_PERMISSION = "ai:trace:view";

    private final InventoryFactsQuery inventoryFacts;
    private final PurchasingFactsQuery purchasingFacts;
    private final SalesFactsQuery salesFacts;
    private final ManufacturingFactsQuery manufacturingFacts;
    private final QualityFactsQuery qualityFacts;
    private final IotFactsPort iotFacts;
    private final Clock clock;

    public TraceabilityApplicationService(InventoryFactsQuery inventoryFacts,
                                          PurchasingFactsQuery purchasingFacts,
                                          SalesFactsQuery salesFacts,
                                          ManufacturingFactsQuery manufacturingFacts,
                                          QualityFactsQuery qualityFacts,
                                          IotFactsPort iotFacts) {
        this(inventoryFacts, purchasingFacts, salesFacts, manufacturingFacts, qualityFacts, iotFacts,
                Clock.systemUTC());
    }

    public TraceabilityApplicationService(InventoryFactsQuery inventoryFacts,
                                          PurchasingFactsQuery purchasingFacts,
                                          SalesFactsQuery salesFacts,
                                          ManufacturingFactsQuery manufacturingFacts,
                                          QualityFactsQuery qualityFacts,
                                          IotFactsPort iotFacts, Clock clock) {
        this.inventoryFacts = inventoryFacts;
        this.purchasingFacts = purchasingFacts;
        this.salesFacts = salesFacts;
        this.manufacturingFacts = manufacturingFacts;
        this.qualityFacts = qualityFacts;
        this.iotFacts = iotFacts;
        this.clock = clock;
    }

    /**
     * 查询追溯链并按租户、链路权限和节点领域权限裁剪。
     * 入参：可信上下文、来源实体类型和标识；出参：节点、关系、隐藏计数和缺口；流程：调用各 Facts 端口、合并真实关系、过滤不可见节点。
     */
    public TraceabilityProjection query(TraceabilityQuery request) {
        requireTracePermission(request.context());
        TraceQueryCollector collector = new TraceQueryCollector(request, clock.instant());
        collect(collector, "inventory", () -> inventoryFacts.trace(request.toPortQuery()));
        collect(collector, "purchasing", () -> purchasingFacts.trace(request.toPortQuery()));
        collect(collector, "sales", () -> salesFacts.trace(request.toPortQuery()));
        collect(collector, "manufacturing", () -> manufacturingFacts.trace(request.toPortQuery()));
        collect(collector, "quality", () -> qualityFacts.trace(request.toPortQuery()));
        collect(collector, "iot", () -> iotFacts.trace(request.toPortQuery()));
        return collector.toProjection();
    }

    /** 供看板使用的追溯完整性摘要，仍由各 Facts 端口提供指标。 */
    public FactsSummary summary(FactsQueryRequest request) {
        requireTraceSummaryPermission(request.context());
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        List<String> sourceNames = new ArrayList<>();
        Instant updatedAt = null;
        for (SourceSummary source : List.of(
                new SourceSummary("inventory", () -> inventoryFacts.traceSummary(request)),
                new SourceSummary("purchasing", () -> purchasingFacts.traceSummary(request)),
                new SourceSummary("sales", () -> salesFacts.traceSummary(request)),
                new SourceSummary("manufacturing", () -> manufacturingFacts.traceSummary(request)),
                new SourceSummary("quality", () -> qualityFacts.traceSummary(request)),
                new SourceSummary("iot", () -> iotFacts.traceSummary(request)))) {
            FactsSummary summary;
            try {
                summary = source.loader().get();
            } catch (GisException exception) {
                // 租户、权限和参数错误必须原样保留稳定业务错误码，不能伪装成事实源不可用。
                throw exception;
            } catch (RuntimeException exception) {
                throw new FactQueryUnavailableException(source.name() + " 事实查询不可用", exception);
            }
            summary.metrics().forEach((key, value) -> metrics.merge(key, value, BigDecimal::add));
            if (!summary.sourceSummary().isBlank()) {
                sourceNames.add(summary.sourceSummary());
            }
            updatedAt = later(updatedAt, summary.sourceUpdatedAt());
        }
        return new FactsSummary(metrics, String.join(", ", sourceNames), updatedAt);
    }

    private static void collect(TraceQueryCollector collector, String source, TraceLoader loader) {
        try {
            collector.add(loader.load());
        } catch (FactQueryUnavailableException exception) {
            collector.missingSources.add(source);
        } catch (GisException exception) {
            // 租户/权限/参数错误不能被伪装成“部分来源缺失”，必须保留稳定业务错误码。
            throw exception;
        } catch (RuntimeException exception) {
            // 未声明的源异常属于依赖不可用，统一转换为稳定的 503，而不是返回误导性的空追溯链。
            throw new FactQueryUnavailableException(source + " 事实查询不可用", exception);
        }
    }

    private static void requireTracePermission(FactsQueryContext context) {
        if (context == null || (!context.hasPermission(TRACE_VIEW_PERMISSION)
                && !context.hasPermission(LEGACY_TRACE_VIEW_PERMISSION))) {
            throw new GisException(GisErrorCode.GIS_AUTH_001, "当前用户无追溯查询权限");
        }
    }

    /** 看板只读摘要允许看板总权限；直接追溯查询仍要求 trace:chain:view。 */
    private static void requireTraceSummaryPermission(FactsQueryContext context) {
        if (context == null || (!context.hasPermission(TRACE_VIEW_PERMISSION)
                && !context.hasPermission("dashboard:view")
                && !context.hasPermission("dashboard:traceability:view"))) {
            throw new GisException(GisErrorCode.GIS_AUTH_001, "当前用户无追溯摘要查询权限");
        }
    }

    private static Instant later(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    @FunctionalInterface
    private interface TraceLoader {
        TraceFacts load();
    }

    private record SourceSummary(String name, java.util.function.Supplier<FactsSummary> loader) {
    }

    private static final class TraceQueryCollector {
        private final TraceabilityQuery request;
        private final Instant generatedAt;
        private final Map<String, TraceNode> nodes = new LinkedHashMap<>();
        private final List<TraceLink> links = new ArrayList<>();
        private final Set<String> missingSources = new LinkedHashSet<>();
        private Instant sourceUpdatedAt;
        private int hiddenNodeCount;

        private TraceQueryCollector(TraceabilityQuery request, Instant generatedAt) {
            this.request = request;
            this.generatedAt = generatedAt;
        }

        private void add(TraceFacts facts) {
            sourceUpdatedAt = later(sourceUpdatedAt, facts.sourceUpdatedAt());
            facts.nodes().forEach(node -> {
                if (!request.context().tenantId().equals(node.tenantId())
                        || (!node.requiredPermission().isBlank()
                        && !request.context().hasPermission(node.requiredPermission()))) {
                    hiddenNodeCount++;
                    return;
                }
                nodes.putIfAbsent(nodeKey(node.entityType(), node.entityId()), node);
            });
            links.addAll(facts.links());
        }

        private TraceabilityProjection toProjection() {
            if (nodes.isEmpty() && hiddenNodeCount == 0) {
                if (missingSources.size() == 6) {
                    throw new GisException(GisErrorCode.GIS_QUERY_002, "全部追溯事实源暂时不可用");
                }
                throw new GisException(GisErrorCode.GIS_POINT_001, "追溯入口不存在或当前用户不可见");
            }
            List<TraceLink> visibleLinks = links.stream()
                    .filter(link -> nodes.containsKey(nodeKey(link.fromType(), link.fromId()))
                            && nodes.containsKey(nodeKey(link.toType(), link.toId())))
                    .distinct()
                    .toList();
            return new TraceabilityProjection(nodes.values().stream().toList(), visibleLinks,
                    hiddenNodeCount, missingSources.stream().toList(), generatedAt, sourceUpdatedAt,
                    request.context().requestId());
        }

        private static String nodeKey(String type, java.util.UUID id) {
            return type + ":" + id;
        }
    }
}
