package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.domain.DashboardTimeRange;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.LowStockItem;
import com.ailearn.platform.core.inventory.application.LowStockPage;
import com.ailearn.platform.core.inventory.application.LowStockQueryService;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.masterdata.application.WarehouseApplicationService;
import com.ailearn.platform.core.purchasing.application.PurchaseOrderApplicationService;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderLineView;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderView;
import com.ailearn.platform.core.sales.application.SalesOrderApplicationService;
import com.ailearn.platform.core.sales.dto.SalesOrderLineView;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 新增业务工具只复用既有只读应用端口，并返回裁剪后的事实。 */
class AiBusinessReadToolsTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final AiRequestContext CONTEXT = new AiRequestContext(
            TENANT_ID, UUID.randomUUID(), "jti", Set.of(), ZoneId.of("Asia/Shanghai"),
            "fingerprint", "request-business-tools");

    @Test
    void shouldReturnSanitizedSalesOrderStatus() {
        UUID orderId = UUID.randomUUID();
        SalesOrderView order = new SalesOrderView();
        order.setId(orderId);
        order.setSoNo("SO-001");
        order.setStatus("Approved");
        order.setFulfillmentStatus("Picking");
        order.setCompletedSessionId("must-not-be-returned");
        order.setLines(List.of(new SalesOrderLineView(UUID.randomUUID(), 1, UUID.randomUUID(), "PCS",
                "SKU-1", "产品一", "10.000000", "8.000000", "6.000000", "2.000000",
                "2.000000", "2.000000", "4.000000", "6.000000", "8.000000")));
        SalesOrderApplicationService service = mock(SalesOrderApplicationService.class);
        when(service.detail(orderId)).thenReturn(order);

        AiToolResult result = new QuerySalesOrderStatusAiTool(service).execute(
                CONTEXT, Map.of("order_id", orderId.toString()));

        QuerySalesOrderStatusAiTool.SalesOrderStatusFact fact =
                (QuerySalesOrderStatusAiTool.SalesOrderStatusFact) result.data();
        assertEquals("SO-001", fact.orderNo());
        assertEquals("Picking", fact.fulfillmentStatus());
        assertFalse(result.data().toString().contains("must-not-be-returned"));
    }

    @Test
    void shouldReturnPurchasePendingQuantities() {
        UUID orderId = UUID.randomUUID();
        PurchaseOrderView order = new PurchaseOrderView();
        order.setId(orderId);
        order.setPoNo("PO-001");
        order.setStatus("Approved");
        order.setCompletedSessionId("must-not-be-returned");
        order.setLines(List.of(new PurchaseOrderLineView(UUID.randomUUID(), 1, UUID.randomUUID(), "PCS",
                "10.000000", "4.000000", "6.000000", UUID.randomUUID(), null)));
        PurchaseOrderApplicationService service = mock(PurchaseOrderApplicationService.class);
        when(service.detail(orderId)).thenReturn(order);

        AiToolResult result = new QueryPurchaseOrderStatusAiTool(service).execute(
                CONTEXT, Map.of("order_id", orderId.toString()));

        QueryPurchaseOrderStatusAiTool.PurchaseOrderStatusFact fact =
                (QueryPurchaseOrderStatusAiTool.PurchaseOrderStatusFact) result.data();
        assertEquals("6.000000", fact.lines().getFirst().pendingQty());
        assertFalse(result.data().toString().contains("must-not-be-returned"));
    }

    @Test
    void shouldPassTrustedTenantIntoInventoryQuery() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryBalance balance = new InventoryBalance(UUID.randomUUID(), TENANT_ID,
                new InventoryDimension(productId, warehouseId, UUID.randomUUID(), "LOT-1"),
                new BigDecimal("12"), new BigDecimal("5"), 1L,
                OffsetDateTime.parse("2026-09-12T10:00:00+08:00"));
        InventoryQueryService service = mock(InventoryQueryService.class);
        when(service.queryBalances(any())).thenReturn(new InventoryBalancePage(List.of(balance), 1, 1, 50));

        AiToolResult result = new QueryInventoryByProductAndWarehouseAiTool(service).execute(
                CONTEXT, Map.of("product_id", productId.toString(), "warehouse_id", warehouseId.toString()));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        @SuppressWarnings("unchecked")
        List<QueryInventoryByProductAndWarehouseAiTool.InventoryBalanceFact> records =
                (List<QueryInventoryByProductAndWarehouseAiTool.InventoryBalanceFact>) data.get("records");
        assertEquals("7.000000", records.getFirst().availableQty());
        verify(service).queryBalances(new InventoryBalanceQuery(
                TENANT_ID, productId, warehouseId, null, null, 1, 50));
    }

    @Test
    void shouldReturnWorkOrderProgressWithoutCompletedSession() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrderFact workOrder = mock(WorkOrderFact.class);
        when(workOrder.id()).thenReturn(workOrderId);
        when(workOrder.workOrderNo()).thenReturn("WO-001");
        when(workOrder.productId()).thenReturn(UUID.randomUUID());
        when(workOrder.plannedQty()).thenReturn(new BigDecimal("10"));
        WorkOrderProgress progress = mock(WorkOrderProgress.class);
        when(progress.completedOperationIds()).thenReturn(Set.of(UUID.randomUUID()));
        when(progress.reportedQty()).thenReturn(new BigDecimal("8"));
        when(progress.qualifiedQty()).thenReturn(new BigDecimal("7"));
        when(progress.defectQty()).thenReturn(BigDecimal.ONE);
        when(progress.receivedQty()).thenReturn(new BigDecimal("6"));
        WorkOrderLifecycle lifecycle = mock(WorkOrderLifecycle.class);
        when(lifecycle.workOrder()).thenReturn(workOrder);
        when(lifecycle.status()).thenReturn(com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus.InProgress);
        when(lifecycle.requiredOperationIds()).thenReturn(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        when(lifecycle.progress()).thenReturn(progress);
        when(lifecycle.completedSessionId()).thenReturn("must-not-be-returned");
        WorkOrderExecutionService service = mock(WorkOrderExecutionService.class);
        when(service.find(workOrderId)).thenReturn(Optional.of(lifecycle));

        AiToolResult result = new QueryWorkOrderProgressAiTool(service).execute(
                CONTEXT, Map.of("work_order_id", workOrderId.toString()));

        QueryWorkOrderProgressAiTool.WorkOrderProgressFact fact =
                (QueryWorkOrderProgressAiTool.WorkOrderProgressFact) result.data();
        assertEquals(2, fact.requiredOperationCount());
        assertEquals("7", fact.qualifiedQty());
        assertFalse(result.data().toString().contains("must-not-be-returned"));
    }

    @Test
    void shouldRejectUnknownOrMissingArguments() {
        QuerySalesOrderStatusAiTool tool = new QuerySalesOrderStatusAiTool(
                mock(SalesOrderApplicationService.class));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(CONTEXT, Map.of("sql", "select 1")));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(CONTEXT, Map.of()));
    }

    @Test
    void shouldQueryQualitySummaryWithTrustedFactsContext() {
        DashboardApplicationService service = mock(DashboardApplicationService.class);
        DashboardSummaryProjection projection = projection(DashboardSummaryType.QUALITY, false);
        DashboardQuery expectedQuery = new DashboardQuery(CONTEXT.toFactsContext(), "7d", Map.of());
        when(service.query(DashboardSummaryType.QUALITY, expectedQuery)).thenReturn(projection);

        AiToolResult result = new QueryQualityStatisticsAiTool(service).execute(
                CONTEXT, Map.of("time_range", "7d"));

        assertEquals(projection, result.data());
        assertEquals("quality facts", result.sourceSummary());
        verify(service).query(DashboardSummaryType.QUALITY, expectedQuery);
    }

    @Test
    void shouldQueryAlarmSummaryWithTenantValidatedDeviceFilter() {
        UUID deviceId = UUID.randomUUID();
        DashboardApplicationService service = mock(DashboardApplicationService.class);
        DashboardSummaryProjection projection = projection(DashboardSummaryType.ALARM, true);
        DashboardQuery expectedQuery = new DashboardQuery(CONTEXT.toFactsContext(), "today",
                Map.of("device_id", deviceId.toString()));
        when(service.query(DashboardSummaryType.ALARM, expectedQuery)).thenReturn(projection);

        AiToolResult result = new QueryDeviceAlarmAiTool(service).execute(
                CONTEXT, Map.of("device_id", deviceId.toString()));

        assertEquals(projection, result.data());
        assertEquals(true, result.timeRangeSummary().contains("陈旧缓存"));
        verify(service).query(DashboardSummaryType.ALARM, expectedQuery);
    }

    @Test
    void shouldQueryLowStockWithFixedSafetyStockRuleAndTrustedTenant() {
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LowStockQueryService service = mock(LowStockQueryService.class);
        when(service.query(TENANT_ID, warehouseId, 25)).thenReturn(new LowStockPage(List.of(
                new LowStockItem(productId, "SKU-LOW", "低库存产品", "PCS",
                        new BigDecimal("10"), new BigDecimal("4"), new BigDecimal("6"),
                        OffsetDateTime.parse("2026-09-12T11:00:00+08:00"))), false));
        WarehouseApplicationService warehouseService = mock(WarehouseApplicationService.class);
        QueryLowStockAiTool tool = new QueryLowStockAiTool(service, warehouseService);

        AiToolResult result = tool.execute(CONTEXT,
                Map.of("warehouse_id", warehouseId.toString(), "limit", 25));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        @SuppressWarnings("unchecked")
        List<QueryLowStockAiTool.LowStockFact> records =
                (List<QueryLowStockAiTool.LowStockFact>) data.get("records");
        assertEquals("available_qty < safety_stock", data.get("rule"));
        assertEquals("6", records.getFirst().shortfallQty());
        verify(warehouseService).detail(warehouseId);
        verify(service).query(TENANT_ID, warehouseId, 25);
    }

    @Test
    void lowStockToolShouldRequireAllThreeReadPermissions() {
        QueryLowStockAiTool tool = new QueryLowStockAiTool(
                mock(LowStockQueryService.class), mock(WarehouseApplicationService.class));
        AiRequestContext partial = new AiRequestContext(TENANT_ID, UUID.randomUUID(), "jti",
                Set.of("inv:product:view", "inv:balance:view"), ZoneId.of("Asia/Shanghai"),
                "fingerprint", "request-low-stock-partial");
        AiRequestContext complete = new AiRequestContext(TENANT_ID, UUID.randomUUID(), "jti",
                Set.of("inv:product:view", "inv:warehouse:view", "inv:balance:view"),
                ZoneId.of("Asia/Shanghai"), "fingerprint", "request-low-stock-complete");

        assertFalse(tool.isAllowed(partial));
        org.junit.jupiter.api.Assertions.assertTrue(tool.isAllowed(complete));
    }

    @Test
    void shouldRejectUnsupportedDashboardArgumentsBeforeQuery() {
        DashboardApplicationService service = mock(DashboardApplicationService.class);
        QueryQualityStatisticsAiTool qualityTool = new QueryQualityStatisticsAiTool(service);
        QueryDeviceAlarmAiTool alarmTool = new QueryDeviceAlarmAiTool(service);

        assertThrows(IllegalArgumentException.class,
                () -> qualityTool.execute(CONTEXT, Map.of("time_range", "365d")));
        assertThrows(IllegalArgumentException.class,
                () -> alarmTool.execute(CONTEXT, Map.of("device_id", "not-a-uuid")));
    }

    @Test
    void shouldComposeSixDailyOperationalSectionsWithoutWritingReport() {
        DashboardApplicationService service = mock(DashboardApplicationService.class);
        for (DashboardSummaryType type : List.of(DashboardSummaryType.INVENTORY,
                DashboardSummaryType.FULFILLMENT, DashboardSummaryType.MANUFACTURING,
                DashboardSummaryType.QUALITY, DashboardSummaryType.DEVICE, DashboardSummaryType.ALARM)) {
            when(service.query(type, new DashboardQuery(CONTEXT.toFactsContext(), "today", Map.of())))
                    .thenReturn(projection(type, false));
        }

        AiToolResult result = new GenerateDailyOperationReportAiTool(service).execute(CONTEXT, Map.of());

        GenerateDailyOperationReportAiTool.DailyOperationReportFacts facts =
                (GenerateDailyOperationReportAiTool.DailyOperationReportFacts) result.data();
        assertEquals(6, facts.sections().size());
        assertEquals("today", facts.timeRange());
    }

    private static DashboardSummaryProjection projection(DashboardSummaryType type, boolean stale) {
        Instant from = Instant.parse("2026-09-12T00:00:00Z");
        Instant to = Instant.parse("2026-09-12T10:00:00Z");
        return new DashboardSummaryProjection(type, Map.of("count", BigDecimal.ONE),
                new DashboardTimeRange("today", from, to), type.key() + " facts", to, to,
                stale, stale ? from : null, CONTEXT.requestId());
    }
}
