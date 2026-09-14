package com.ailearn.platform.core.inventory;

import com.ailearn.platform.core.inventory.application.LowStockPage;
import com.ailearn.platform.core.inventory.infrastructure.LowStockMapper;
import com.ailearn.platform.core.inventory.infrastructure.LowStockRow;
import com.ailearn.platform.core.inventory.infrastructure.PostgresLowStockQueryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 低库存读模型必须按租户聚合、限制结果数量并稳定标记截断。 */
class PostgresLowStockQueryServiceTest {

    @Test
    void shouldMapRowsAndUseOneExtraRecordForTruncation() {
        UUID tenantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        LowStockMapper mapper = mock(LowStockMapper.class);
        when(mapper.selectLowStock(tenantId, warehouseId, 2)).thenReturn(List.of(
                row("SKU-1", "10", "3", "7"), row("SKU-2", "5", "4", "1")));
        PostgresLowStockQueryService service = new PostgresLowStockQueryService(mapper);

        LowStockPage page = service.query(tenantId, warehouseId, 1);

        assertTrue(page.truncated());
        assertEquals(1, page.records().size());
        assertEquals(new BigDecimal("7"), page.records().getFirst().shortfallQty());
        verify(mapper).selectLowStock(tenantId, warehouseId, 2);
    }

    @Test
    void shouldRejectMissingTenantOrUnboundedLimit() {
        PostgresLowStockQueryService service = new PostgresLowStockQueryService(mock(LowStockMapper.class));
        assertThrows(IllegalArgumentException.class, () -> service.query(null, null, 10));
        assertThrows(IllegalArgumentException.class, () -> service.query(UUID.randomUUID(), null, 101));
    }

    private static LowStockRow row(String sku, String safety, String available, String shortfall) {
        LowStockRow row = new LowStockRow();
        row.setProductId(UUID.randomUUID());
        row.setSku(sku);
        row.setProductName(sku + " product");
        row.setUom("PCS");
        row.setSafetyStock(new BigDecimal(safety));
        row.setAvailableQty(new BigDecimal(available));
        row.setShortfallQty(new BigDecimal(shortfall));
        return row;
    }
}
