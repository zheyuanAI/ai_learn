package com.ailearn.platform.core.ai;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.tool.GenerateDailyOperationReportAiTool;
import com.ailearn.platform.core.ai.tool.QueryDeviceAlarmAiTool;
import com.ailearn.platform.core.ai.tool.QueryInventoryByProductAndWarehouseAiTool;
import com.ailearn.platform.core.ai.tool.QueryLowStockAiTool;
import com.ailearn.platform.core.ai.tool.QueryOperationAuditAiTool;
import com.ailearn.platform.core.ai.tool.QueryPurchaseOrderStatusAiTool;
import com.ailearn.platform.core.ai.tool.QueryQualityStatisticsAiTool;
import com.ailearn.platform.core.ai.tool.QuerySalesOrderStatusAiTool;
import com.ailearn.platform.core.ai.tool.QueryTraceAiTool;
import com.ailearn.platform.core.ai.tool.QueryWorkOrderProgressAiTool;
import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.LowStockQueryService;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.masterdata.application.WarehouseApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.purchasing.application.PurchaseOrderApplicationService;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.sales.application.SalesOrderApplicationService;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 十个 AI 工具的固定目录、安全 Schema 和只读权限边界契约。 */
class AiToolCatalogContractTest {
    private static final Set<String> EXPECTED_NAMES = Set.of(
            "queryTrace", "queryOperationAudit", "querySalesOrderStatus",
            "queryPurchaseOrderStatus", "queryInventoryByProductAndWarehouse",
            "queryWorkOrderProgress", "queryQualityStatistics", "queryDeviceAlarm",
            "queryLowStock", "generateDailyOperationReport");

    @Test
    void catalogMustContainOnlyTenStrictReadTools() {
        List<AiReadTool> tools = tools();
        AiRequestContext noPermission = context(Set.of());
        AiRequestContext writeOnly = context(Set.of(
                "sales:order:delete", "inv:balance:adjust", "auth:role:edit"));

        assertEquals(EXPECTED_NAMES, tools.stream().map(AiReadTool::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        for (AiReadTool tool : tools) {
            assertFalse(tool.anyOfPermissions().isEmpty(), tool.name() + " 必须声明领域查看权限");
            assertFalse(tool.isAllowed(noPermission), tool.name() + " 不得对无权限用户开放");
            assertFalse(tool.isAllowed(writeOnly), tool.name() + " 不得因写权限而开放");
            Map<String, Object> schema = tool.parametersSchema();
            assertEquals("object", schema.get("type"), tool.name() + " 必须使用对象参数");
            assertEquals(Boolean.FALSE, schema.get("additionalProperties"),
                    tool.name() + " 必须拒绝未声明参数");
            String normalized = tool.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(List.of("create", "update", "delete", "adjust", "complete", "approve")
                    .stream().noneMatch(normalized::contains), tool.name() + " 不得表达写操作");
        }
    }

    private static List<AiReadTool> tools() {
        DashboardApplicationService dashboard = mock(DashboardApplicationService.class);
        return List.of(
                new QueryTraceAiTool(mock(TraceabilityApplicationService.class)),
                new QueryOperationAuditAiTool(mock(OperationAuditApplicationService.class)),
                new QuerySalesOrderStatusAiTool(mock(SalesOrderApplicationService.class)),
                new QueryPurchaseOrderStatusAiTool(mock(PurchaseOrderApplicationService.class)),
                new QueryInventoryByProductAndWarehouseAiTool(mock(InventoryQueryService.class)),
                new QueryWorkOrderProgressAiTool(mock(WorkOrderExecutionService.class)),
                new QueryQualityStatisticsAiTool(dashboard),
                new QueryDeviceAlarmAiTool(dashboard),
                new QueryLowStockAiTool(mock(LowStockQueryService.class),
                        mock(WarehouseApplicationService.class)),
                new GenerateDailyOperationReportAiTool(dashboard));
    }

    private static AiRequestContext context(Set<String> permissions) {
        return new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti", permissions,
                ZoneId.of("Asia/Shanghai"), "permission-fingerprint", "request-ai-catalog");
    }
}
