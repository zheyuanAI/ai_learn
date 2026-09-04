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
            if (!"purchase_order".equalsIgnoreCase(query.entityType())) {
                return TraceFacts.empty("purchasing");
            }
            Optional<PurchaseOrder> order = repository.findById(query.context().tenantId(), query.entityId());
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
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("purchasing", exception);
        }
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
