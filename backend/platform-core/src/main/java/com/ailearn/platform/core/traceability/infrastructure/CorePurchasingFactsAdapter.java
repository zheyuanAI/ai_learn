package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.purchasing.domain.PurchaseOrder;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPage;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPageQuery;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderRepository;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderStatus;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.PurchasingFactsQuery;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 采购订单/收货事实的 S7 只读适配器，复用采购订单持久化端口。 */
public class CorePurchasingFactsAdapter implements PurchasingFactsQuery {
    private static final int PAGE_SIZE = 200;
    private final PurchaseOrderRepository repository;

    public CorePurchasingFactsAdapter(PurchaseOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public FactsSummary fulfillment(FactsQueryRequest request) {
        try {
            List<PurchaseOrder> orders = orders(request.context().tenantId());
            BigDecimal ordered = BigDecimal.ZERO;
            BigDecimal received = BigDecimal.ZERO;
            long completed = 0;
            long orderCount = 0;
            Instant updated = null;
            for (PurchaseOrder order : orders) {
                if (!FactsAdapterSupport.inRange(order.updatedAt() == null ? order.createdAt() : order.updatedAt(), request)) {
                    continue;
                }
                orderCount++;
                if (order.status() == PurchaseOrderStatus.Completed) {
                    completed++;
                }
                for (PurchaseOrderLine line : order.lines()) {
                    ordered = FactsAdapterSupport.add(ordered, line.orderedQty());
                    received = FactsAdapterSupport.add(received, line.receivedQty());
                }
                updated = FactsAdapterSupport.later(updated,
                        FactsAdapterSupport.instant(order.updatedAt() == null ? order.createdAt() : order.updatedAt()));
            }
            return new FactsSummary(Map.of("purchase_order_count", BigDecimal.valueOf(orderCount),
                    "ordered_qty", ordered, "received_qty", received,
                    "completed_order_count", BigDecimal.valueOf(completed)), "purchasing order", updated);
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("purchasing", exception);
        }
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        FactsSummary summary = fulfillment(request);
        Map<String, BigDecimal> metrics = new LinkedHashMap<>(summary.metrics());
        metrics.put("traceable_order_count", metrics.getOrDefault("purchase_order_count", BigDecimal.ZERO));
        return new FactsSummary(metrics, "purchasing order", summary.sourceUpdatedAt());
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        try {
            String type = query.entityType().trim().toLowerCase(java.util.Locale.ROOT);
            UUID tenantId = query.context().tenantId();
            if ("purchase_order".equals(type)) {
                return orderFacts(tenantId, query.entityId());
            }
            if ("purchase_order_line".equals(type)) {
                return lineFacts(tenantId, query.entityId());
            }
            if ("work_order".equals(type)) {
                return sourceWorkOrderFacts(tenantId, query.entityId());
            }
            return TraceFacts.empty("purchasing");
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("purchasing", exception);
        }
    }

    /** 从真实采购订单聚合展开采购订单及明细节点。 */
    private TraceFacts orderFacts(UUID tenantId, UUID orderId) {
        Optional<PurchaseOrder> order = repository.findById(tenantId, orderId);
            if (order.isEmpty()) {
                return TraceFacts.empty("purchasing order");
            }
            PurchaseOrder value = order.get();
            List<TraceNode> nodes = new ArrayList<>();
            List<TraceLink> links = new ArrayList<>();
            nodes.add(new TraceNode(value.tenantId(), "purchase_order", value.id(), value.poNo(),
                    value.status().name(), "pur:order:view", FactsAdapterSupport.instant(value.updatedAt()), true));
            for (PurchaseOrderLine line : value.lines()) {
                nodes.add(new TraceNode(value.tenantId(), "purchase_order_line", line.id(),
                        "line-" + line.lineNo(), value.status().name(), "pur:order:view",
                        FactsAdapterSupport.instant(value.updatedAt()), true));
                links.add(new TraceLink("purchase_order", value.id(), "purchase_order_line", line.id(), "order_line"));
            }
            return new TraceFacts(nodes, links, FactsAdapterSupport.instant(value.updatedAt()), "purchasing order");
    }

    /** 通过真实采购订单反向解析采购订单行。 */
    private TraceFacts lineFacts(UUID tenantId, UUID lineId) {
        for (PurchaseOrder order : orders(tenantId)) {
            Optional<PurchaseOrderLine> found = order.lines().stream()
                    .filter(line -> line.id().equals(lineId)).findFirst();
            if (found.isEmpty()) {
                continue;
            }
            PurchaseOrderLine line = found.get();
            Instant updated = FactsAdapterSupport.instant(order.updatedAt() == null
                    ? order.createdAt() : order.updatedAt());
            TraceNode orderNode = new TraceNode(tenantId, "purchase_order", order.id(), order.poNo(),
                    order.status().name(), "pur:order:view", updated, true);
            TraceNode lineNode = new TraceNode(tenantId, "purchase_order_line", line.id(),
                    "line-" + line.lineNo(), order.status().name(), "pur:order:view", updated, true);
            return new TraceFacts(List.of(lineNode, orderNode),
                    List.of(new TraceLink("purchase_order", order.id(), "purchase_order_line", line.id(), "order_line")),
                    updated, "purchasing order line");
        }
        return TraceFacts.empty("purchasing order line");
    }

    /** 从真实采购订单行的 sourceWorkOrderId 关系反向展开采购事实。 */
    private TraceFacts sourceWorkOrderFacts(UUID tenantId, UUID workOrderId) {
        List<TraceNode> nodes = new ArrayList<>();
        List<TraceLink> links = new ArrayList<>();
        Instant updated = null;
        for (PurchaseOrder order : orders(tenantId)) {
            Instant orderUpdated = FactsAdapterSupport.instant(order.updatedAt() == null
                    ? order.createdAt() : order.updatedAt());
            List<PurchaseOrderLine> matches = order.lines().stream()
                    .filter(line -> workOrderId.equals(line.sourceWorkOrderId())).toList();
            if (matches.isEmpty()) {
                continue;
            }
            nodes.add(new TraceNode(tenantId, "purchase_order", order.id(), order.poNo(),
                    order.status().name(), "pur:order:view", orderUpdated, true));
            updated = FactsAdapterSupport.later(updated, orderUpdated);
            for (PurchaseOrderLine line : matches) {
                nodes.add(new TraceNode(tenantId, "purchase_order_line", line.id(),
                        "line-" + line.lineNo(), order.status().name(), "pur:order:view", orderUpdated, true));
                links.add(new TraceLink("work_order", workOrderId, "purchase_order_line", line.id(),
                        "source_purchase_order"));
                links.add(new TraceLink("purchase_order", order.id(), "purchase_order_line", line.id(), "order_line"));
            }
        }
        return nodes.isEmpty() ? TraceFacts.empty("purchasing source work order")
                : new TraceFacts(nodes, links, updated, "purchasing source work order");
    }

    private List<PurchaseOrder> orders(UUID tenantId) {
        List<PurchaseOrder> result = new ArrayList<>();
        PurchaseOrderPage page = repository.findPage(tenantId, new PurchaseOrderPageQuery(null, null, 1, PAGE_SIZE));
        result.addAll(page.records());
        for (int current = 2; (long) (current - 1) * PAGE_SIZE < page.total(); current++) {
            page = repository.findPage(tenantId, new PurchaseOrderPageQuery(null, null, current, PAGE_SIZE));
            result.addAll(page.records());
        }
        return result;
    }
}
