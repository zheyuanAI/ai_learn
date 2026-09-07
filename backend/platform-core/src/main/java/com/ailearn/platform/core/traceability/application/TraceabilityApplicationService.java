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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
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
    private static final int MAX_QUERIES = 256;
    private static final int MAX_NODES = 256;
    private static final int MAX_LINKS = 512;
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
        if (request == null) {
            throw new GisException(GisErrorCode.GIS_QUERY_001, "追溯查询不能为空");
        }
        requireTracePermission(request.context());
        TraceQueryCollector collector = new TraceQueryCollector(request, clock.instant());
        Deque<com.ailearn.platform.core.traceability.ports.TraceQuery> queue = new ArrayDeque<>();
        Set<String> queried = new HashSet<>();
        queue.add(request.toPortQuery());
        int queryCount = 0;
        while (!queue.isEmpty() && queryCount < MAX_QUERIES && !collector.limitReached()) {
            com.ailearn.platform.core.traceability.ports.TraceQuery current = queue.removeFirst();
            if (!queried.add(nodeKey(current.entityType(), current.entityId()))) {
                continue;
            }
            queryCount++;
            List<com.ailearn.platform.core.traceability.ports.TraceNode> discovered = new ArrayList<>();
            discovered.addAll(collect(collector, "inventory", () -> inventoryFacts.trace(current)));
            discovered.addAll(collect(collector, "purchasing", () -> purchasingFacts.trace(current)));
            discovered.addAll(collect(collector, "sales", () -> salesFacts.trace(current)));
            discovered.addAll(collect(collector, "manufacturing", () -> manufacturingFacts.trace(current)));
            discovered.addAll(collect(collector, "quality", () -> qualityFacts.trace(current)));
            discovered.addAll(collect(collector, "iot", () -> iotFacts.trace(current)));
            for (com.ailearn.platform.core.traceability.ports.TraceNode node : discovered) {
                if (!collector.limitReached()) {
                    queue.addLast(new com.ailearn.platform.core.traceability.ports.TraceQuery(
                            request.context(), node.entityType(), node.entityId()));
                }
            }
        }
        if (!queue.isEmpty() || queryCount >= MAX_QUERIES) {
            collector.markTruncated();
        }
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

    /**
     * 调用单个事实源并把异常分类：源不可用记录为缺口，租户/权限/参数错误继续向上抛出，避免把拒绝访问伪装成部分成功。
     */
    private static List<TraceNode> collect(TraceQueryCollector collector, String source, TraceLoader loader) {
        try {
            return collector.add(loader.load());
        } catch (FactQueryUnavailableException exception) {
            collector.missingSources.add(source);
            return List.of();
        } catch (GisException exception) {
            // 租户/权限/参数错误不能被伪装成“部分来源缺失”，必须保留稳定业务错误码。
            throw exception;
        } catch (RuntimeException exception) {
            // 未声明的源异常属于依赖不可用，统一转换为稳定的 503，而不是返回误导性的空追溯链。
            throw new FactQueryUnavailableException(source + " 事实查询不可用", exception);
        }
    }

    /** 使用实体类型和 ID 生成 BFS 去重键，防止同一节点被不同事实源重复展开。 */
    private static String nodeKey(String entityType, java.util.UUID entityId) {
        return entityType + ":" + entityId;
    }

    /** 校验直接追溯链权限；兼容旧权限码只用于读取，不改变当前租户和节点权限裁剪。 */
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

    /** 合并多个事实源更新时间，保留非空且较新的时间作为投影 freshness 标记。 */
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
        /** 从一个领域 Facts 端口加载当前追溯节点关联的事实。 */
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
        private final Set<String> hiddenNodeKeys = new HashSet<>();
        private Instant sourceUpdatedAt;
        private int hiddenNodeCount;
        private boolean truncated;

        /** 初始化一次追溯请求的聚合状态和生成时间。 */
        private TraceQueryCollector(TraceabilityQuery request, Instant generatedAt) {
            this.request = request;
            this.generatedAt = generatedAt;
        }

        /**
         * 合并事实源节点和关系，同时执行租户/节点权限裁剪及节点、关系数量上限控制。
         * 被隐藏的节点只累计计数，不进入下一轮 BFS，避免通过关系间接扩散不可见数据。
         */
        private List<TraceNode> add(TraceFacts facts) {
            if (facts == null) {
                return List.of();
            }
            List<TraceNode> discovered = new ArrayList<>();
            sourceUpdatedAt = later(sourceUpdatedAt, facts.sourceUpdatedAt());
            facts.nodes().forEach(node -> {
                if (node == null) {
                    return;
                }
                if (!request.context().tenantId().equals(node.tenantId())
                        || (!node.requiredPermission().isBlank()
                        && !request.context().hasPermission(node.requiredPermission()))) {
                    if (hiddenNodeKeys.add(nodeKey(node.entityType(), node.entityId()))) {
                        hiddenNodeCount++;
                    }
                    return;
                }
                if (!nodes.containsKey(nodeKey(node.entityType(), node.entityId()))) {
                    if (nodes.size() >= MAX_NODES) {
                        truncated = true;
                        return;
                    }
                    nodes.put(nodeKey(node.entityType(), node.entityId()), node);
                    discovered.add(node);
                }
            });
            if (links.size() >= MAX_LINKS) {
                truncated = true;
            } else {
                int remaining = MAX_LINKS - links.size();
                if (facts.links().size() > remaining) {
                    truncated = true;
                }
                links.addAll(facts.links().stream().limit(remaining).toList());
            }
            return List.copyOf(discovered);
        }

        /** 判断节点或关系是否已达到投影上限，达到后停止继续展开 BFS。 */
        private boolean limitReached() {
            return nodes.size() >= MAX_NODES || links.size() >= MAX_LINKS;
        }

        /** 标记本次结果因查询、节点或关系上限而被截断。 */
        private void markTruncated() {
            truncated = true;
        }

        /**
         * 生成最终追溯投影；全部事实源不可用和入口不可见分别映射为受控错误，关系只保留两端均可见的边。
         */
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
                    request.context().requestId(), truncated);
        }

        /** 生成收集器内部使用的节点去重键。 */
        private static String nodeKey(String type, java.util.UUID id) {
            return type + ":" + id;
        }
    }
}
