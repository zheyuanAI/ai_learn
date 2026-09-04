package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderPage;
import com.ailearn.platform.core.sales.domain.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.SalesFactsQuery;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 销售订单/履约事实的 S7 只读适配器，复用销售订单持久化端口。 */
public class CoreSalesFactsAdapter implements SalesFactsQuery {
    private static final int PAGE_SIZE = 200;
    private final SalesOrderRepository repository;

    public CoreSalesFactsAdapter(SalesOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public FactsSummary fulfillment(FactsQueryRequest request) {
        try {
            List<SalesOrder> orders = orders(request.context().tenantId());
            BigDecimal ordered = BigDecimal.ZERO;
            BigDecimal reserved = BigDecimal.ZERO;
            BigDecimal picked = BigDecimal.ZERO;
            BigDecimal shipped = BigDecimal.ZERO;
            long completed = 0;
            long orderCount = 0;
            Instant updated = null;
            for (SalesOrder order : orders) {
                Instant orderUpdated = FactsAdapterSupport.instant(order.updatedAt() == null
                        ? order.createdAt() : order.updatedAt());
                if (!FactsAdapterSupport.inRange(order.updatedAt() == null ? order.createdAt() : order.updatedAt(), request)) {
                    continue;
                }
                orderCount++;
                if (order.status().name().equalsIgnoreCase("Completed")) {
                    completed++;
                }
                for (SalesOrderLine line : order.lines()) {
                    ordered = FactsAdapterSupport.add(ordered, line.orderedQty());
                    reserved = FactsAdapterSupport.add(reserved, line.reservedQty());
                    picked = FactsAdapterSupport.add(picked, line.pickedQty());
                    shipped = FactsAdapterSupport.add(shipped, line.shippedQty());
                }
                updated = FactsAdapterSupport.later(updated, orderUpdated);
            }
            return new FactsSummary(Map.of("sales_order_count", BigDecimal.valueOf(orderCount),
                    "ordered_qty", ordered, "reserved_qty", reserved, "picked_qty", picked,
                    "shipped_qty", shipped, "completed_order_count", BigDecimal.valueOf(completed)),
                    "sales order", updated);
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("sales", exception);
        }
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        FactsSummary summary = fulfillment(request);
        return new FactsSummary(summary.metrics(), "sales order", summary.sourceUpdatedAt());
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        try {
            if (!"sales_order".equalsIgnoreCase(query.entityType())) {
                return TraceFacts.empty("sales");
            }
            Optional<SalesOrder> order = repository.findById(query.context().tenantId(), query.entityId());
            if (order.isEmpty()) {
                return TraceFacts.empty("sales order");
            }
            SalesOrder value = order.get();
            Instant updated = FactsAdapterSupport.instant(value.updatedAt() == null
                    ? value.createdAt() : value.updatedAt());
            List<TraceNode> nodes = new ArrayList<>();
            List<TraceLink> links = new ArrayList<>();
            nodes.add(new TraceNode(value.tenantId(), "sales_order", value.id(), value.soNo(),
                    value.status().name(), "sales:order:view", updated, true));
            for (SalesOrderLine line : value.lines()) {
                nodes.add(new TraceNode(value.tenantId(), "sales_order_line", line.id(),
                        "line-" + line.lineNo(), value.fulfillmentStatus().name(), "sales:order:view",
                        updated, true));
                links.add(new TraceLink("sales_order", value.id(), "sales_order_line", line.id(), "order_line"));
            }
            return new TraceFacts(nodes, links, updated, "sales order");
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("sales", exception);
        }
    }

    private List<SalesOrder> orders(UUID tenantId) {
        List<SalesOrder> result = new ArrayList<>();
        SalesOrderPage page = repository.findPage(tenantId, new SalesOrderPageQuery(null, null, null, null, 1, PAGE_SIZE));
        result.addAll(page.records());
        for (int current = 2; (long) (current - 1) * PAGE_SIZE < page.total(); current++) {
            page = repository.findPage(tenantId, new SalesOrderPageQuery(null, null, null, null, current, PAGE_SIZE));
            result.addAll(page.records());
        }
        return result;
    }
}
