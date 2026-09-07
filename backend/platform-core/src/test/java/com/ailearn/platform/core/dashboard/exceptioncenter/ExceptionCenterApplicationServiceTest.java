package com.ailearn.platform.core.dashboard.exceptioncenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.dashboard.exceptioncenter.application.ExceptionCenterApplicationServiceImpl;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 异常中心只从库存、制造和 IoT Facts 摘要派生记录，并验证租户权限边界。 */
class ExceptionCenterApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("a8000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-04T02:00:00Z");

    @Test
    void derivesExceptionRecordsAndSupportsSourceFiltering() {
        InventoryFactsQuery inventory = mock(InventoryFactsQuery.class);
        ManufacturingFactsQuery manufacturing = mock(ManufacturingFactsQuery.class);
        IotFactsPort iot = mock(IotFactsPort.class);
        when(inventory.inventory(any())).thenReturn(new FactsSummary(Map.of(
                "available_qty", new BigDecimal("-1"), "on_hand_qty", new BigDecimal("5"),
                "reserved_qty", new BigDecimal("7")), "inventory", NOW));
        when(manufacturing.manufacturing(any())).thenReturn(new FactsSummary(Map.of(
                "quality_blocked_count", BigDecimal.ONE, "defect_qty", new BigDecimal("2")),
                "manufacturing", NOW));
        when(iot.alarm(any())).thenReturn(new FactsSummary(Map.of(
                "triggered_count", BigDecimal.ONE, "recovered_unacked_count", BigDecimal.ONE),
                "iot alarm", NOW));
        ExceptionCenterApplicationServiceImpl service = new ExceptionCenterApplicationServiceImpl(
                inventory, manufacturing, iot, Clock.fixed(NOW, ZoneId.of("UTC")));
        FactsQueryContext context = new FactsQueryContext(TENANT_ID, "exception-center",
                Set.of("dashboard:view"), ZoneId.of("Asia/Shanghai"), "request-exception-center");

        var all = service.query(context, "today", null, null, 1, 20);
        var inventoryOnly = service.query(context, "today", "inventory", null, 1, 20);

        assertEquals(6, all.total());
        assertEquals(2, inventoryOnly.total());
        assertEquals(2, inventoryOnly.records().size());
    }

    @Test
    void rejectsContextWithoutDashboardPermission() {
        ExceptionCenterApplicationServiceImpl service = new ExceptionCenterApplicationServiceImpl(
                mock(InventoryFactsQuery.class), mock(ManufacturingFactsQuery.class), mock(IotFactsPort.class));
        FactsQueryContext context = new FactsQueryContext(TENANT_ID, "no-dashboard", Set.of(),
                ZoneId.of("UTC"), "request-exception-center-denied");

        assertThrows(GisException.class, () -> service.query(context, "today", null, null, 1, 20));
    }
}
