package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.LowStockPage;
import com.ailearn.platform.core.inventory.application.LowStockQueryService;
import com.ailearn.platform.core.inventory.controller.InventoryController;
import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.controller.OperationAuditController;
import com.ailearn.platform.shared.context.TenantContextHolder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI 与页面共用的低库存和操作时间线入口必须只补可信租户后调用现有应用服务。 */
class AiSharedBusinessQueryControllerTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDelegateLowStockToExistingQueryService() {
        TenantContextHolder.setTenantId(TENANT_ID);
        LowStockQueryService lowStock = mock(LowStockQueryService.class);
        UUID warehouseId = UUID.randomUUID();
        when(lowStock.query(TENANT_ID, warehouseId, 20)).thenReturn(new LowStockPage(List.of(), false));
        InventoryController controller = new InventoryController(mock(InventoryQueryService.class), lowStock);

        controller.lowStock(warehouseId, 20);

        verify(lowStock).query(TENANT_ID, warehouseId, 20);
    }

    @Test
    void shouldDelegateSalesAuditWithTrustedTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
        OperationAuditApplicationService service = mock(OperationAuditApplicationService.class);
        when(service.query(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        OperationAuditController controller = new OperationAuditController(service);
        UUID orderId = UUID.randomUUID();

        controller.query("sales_order", orderId, null, null, 50);

        ArgumentCaptor<OperationAuditQuery> captor = ArgumentCaptor.forClass(OperationAuditQuery.class);
        verify(service).query(captor.capture());
        assertEquals(TENANT_ID, captor.getValue().tenantId());
        assertEquals("SALES_ORDER", captor.getValue().entityType());
        assertEquals(orderId, captor.getValue().entityId());
    }
}
