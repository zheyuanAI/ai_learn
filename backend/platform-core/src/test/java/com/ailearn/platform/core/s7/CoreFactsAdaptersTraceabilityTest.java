package com.ailearn.platform.core.s7;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycleRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderPage;
import com.ailearn.platform.core.sales.domain.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.dto.TraceabilityQuery;
import com.ailearn.platform.core.traceability.infrastructure.CoreManufacturingFactsAdapter;
import com.ailearn.platform.core.traceability.infrastructure.CoreSalesFactsAdapter;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.PurchasingFactsQuery;
import com.ailearn.platform.core.traceability.ports.QualityFactsQuery;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 使用真实 Core 销售、制造 Facts 适配器组合验证 S7 从销售订单行追到来源工单。 */
class CoreFactsAdaptersTraceabilityTest {
    private static final UUID TENANT_ID = UUID.fromString("a7000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b7000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("c7000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime TIME = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void bfsCombinesActualSalesAndManufacturingAdaptersThroughSourceLine() {
        UUID orderId = UUID.fromString("d7000000-0000-0000-0000-000000000001");
        UUID lineId = UUID.fromString("e7000000-0000-0000-0000-000000000001");
        UUID workOrderId = UUID.fromString("f7000000-0000-0000-0000-000000000001");
        UUID customerId = UUID.fromString("a7000000-0000-0000-0000-000000000002");
        UUID bomId = UUID.fromString("b7000000-0000-0000-0000-000000000002");
        UUID routingId = UUID.fromString("c7000000-0000-0000-0000-000000000002");
        SalesOrderLine line = new SalesOrderLine(lineId, TENANT_ID, 1, PRODUCT_ID, "PCS",
                qty("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        SalesOrder order = new SalesOrder(orderId, TENANT_ID, "SO-S7-1", customerId,
                java.time.LocalDate.of(2026, 9, 10), SalesOrderStatus.Approved, null, null,
                null, null, null, "S7", 0, USER_ID, TIME, USER_ID, TIME, List.of(line));
        WorkOrderFact workOrder = new WorkOrderFact(workOrderId, TENANT_ID, "WO-S7-1", PRODUCT_ID,
                qty("10"), TIME, TIME.plusHours(8), bomId, "V1", routingId, "V1", lineId,
                WorkOrderStatus.Released, false, USER_ID, TIME);

        SalesOrderRepository salesRepository = mock(SalesOrderRepository.class);
        when(salesRepository.findPage(eq(TENANT_ID), any(SalesOrderPageQuery.class)))
                .thenReturn(new SalesOrderPage(List.of(order), 1, 1, 200));
        when(salesRepository.findById(TENANT_ID, orderId)).thenReturn(Optional.of(order));
        FoundationRepository foundation = mock(FoundationRepository.class);
        when(foundation.findWorkOrders(TENANT_ID)).thenReturn(List.of(workOrder));
        when(foundation.findWorkOrder(TENANT_ID, workOrderId)).thenReturn(Optional.of(workOrder));

        WorkOrderLifecycleRepository lifecycleRepository = mock(WorkOrderLifecycleRepository.class);
        OperationExecutionRepository operationRepository = mock(OperationExecutionRepository.class);
        ProductionFactRepository productionRepository = mock(ProductionFactRepository.class);
        InventoryFactsQuery inventory = emptyInventoryFacts();
        PurchasingFactsQuery purchasing = emptyPurchasingFacts();
        QualityFactsQuery quality = emptyQualityFacts();
        IotFactsPort iot = emptyIotFacts();
        ManufacturingFactsQuery manufacturing = new CoreManufacturingFactsAdapter(foundation,
                lifecycleRepository, operationRepository, productionRepository);
        TraceabilityApplicationService service = new TraceabilityApplicationService(inventory, purchasing,
                new CoreSalesFactsAdapter(salesRepository), manufacturing, quality, iot,
                java.time.Clock.fixed(TIME.toInstant(), ZoneId.of("UTC")));
        FactsQueryContext context = new FactsQueryContext(TENANT_ID, "s7-adapters",
                Set.of("trace:chain:view", "sales:order:view", "mes:workorder:view"),
                ZoneId.of("Asia/Shanghai"), "request-s7-adapters");

        var projection = service.query(new TraceabilityQuery(context, "sales_order", orderId));

        assertTrue(projection.nodes().stream().anyMatch(node -> node.entityType().equals("sales_order_line")
                && node.entityId().equals(lineId)));
        assertTrue(projection.nodes().stream().anyMatch(node -> node.entityType().equals("work_order")
                && node.entityId().equals(workOrderId)));
        assertTrue(projection.links().stream().anyMatch(link -> link.relation().equals("source_work_order")
                && link.toId().equals(workOrderId)));
    }

    private static BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }

    private static InventoryFactsQuery emptyInventoryFacts() {
        InventoryFactsQuery value = mock(InventoryFactsQuery.class);
        when(value.trace(any())).thenReturn(TraceFacts.empty("inventory"));
        return value;
    }

    private static PurchasingFactsQuery emptyPurchasingFacts() {
        PurchasingFactsQuery value = mock(PurchasingFactsQuery.class);
        when(value.trace(any())).thenReturn(TraceFacts.empty("purchasing"));
        return value;
    }

    private static QualityFactsQuery emptyQualityFacts() {
        QualityFactsQuery value = mock(QualityFactsQuery.class);
        when(value.trace(any())).thenReturn(TraceFacts.empty("quality"));
        return value;
    }

    private static IotFactsPort emptyIotFacts() {
        IotFactsPort value = mock(IotFactsPort.class);
        when(value.trace(any())).thenReturn(TraceFacts.empty("iot"));
        return value;
    }
}
