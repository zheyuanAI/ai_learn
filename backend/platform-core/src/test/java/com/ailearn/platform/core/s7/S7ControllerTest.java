package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.controller.DashboardController;
import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.domain.DashboardTimeRange;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import com.ailearn.platform.core.gis.application.GisApplicationService;
import com.ailearn.platform.core.gis.controller.GisController;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.controller.TraceabilityController;
import com.ailearn.platform.core.traceability.dto.TraceabilityProjection;
import com.ailearn.platform.core.traceability.dto.TraceabilityQuery;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S7 REST 入口的权限声明、租户上下文和白名单筛选 focused 测试。 */
class S7ControllerTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ENTITY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldRejectGisProjectionWithoutServerSideMapId() {
        bindContext(Set.of("gis:map:view"));
        GisController controller = new GisController(mock(GisApplicationService.class),
                new TrustedFactsQueryContextFactory());

        GisException exception = assertThrows(GisException.class,
                () -> controller.projection(null, null, null, null));

        assertEquals("GIS_QUERY_001", exception.getBusinessCode());
    }

    @Test
    void shouldDropUnknownDashboardTenantFilterAndPassTrustedContext() {
        bindContext(Set.of("dashboard:view"));
        DashboardApplicationService service = mock(DashboardApplicationService.class);
        DashboardSummaryProjection projection = new DashboardSummaryProjection(
                DashboardSummaryType.INVENTORY,
                Map.of("inventory_count", BigDecimal.ONE),
                new DashboardTimeRange("today", Instant.parse("2026-09-03T16:00:00Z"),
                        Instant.parse("2026-09-04T01:00:00Z")),
                "inventory", Instant.parse("2026-09-04T01:00:00Z"),
                Instant.parse("2026-09-04T00:00:00Z"), false, null, "request-s7");
        when(service.query(eq(DashboardSummaryType.INVENTORY), any(DashboardQuery.class)))
                .thenReturn(projection);
        DashboardController controller = new DashboardController(service,
                new TrustedFactsQueryContextFactory());

        controller.inventory(Map.of("time_range", "today", "warehouse_id", ENTITY_ID.toString(),
                "tenant_id", UUID.randomUUID().toString()));

        var query = org.mockito.ArgumentCaptor.forClass(DashboardQuery.class);
        verify(service).query(eq(DashboardSummaryType.INVENTORY), query.capture());
        assertEquals(TENANT, query.getValue().context().tenantId());
        assertEquals(Map.of("warehouse_id", ENTITY_ID.toString()), query.getValue().filters());
        assertNotNull(query.getValue().context().permissionFingerprint());
    }

    @Test
    void shouldBuildTraceabilityQueryFromTrustedTenantAndRequireTracePermission() {
        bindContext(Set.of("trace:chain:view"));
        TraceabilityApplicationService service = mock(TraceabilityApplicationService.class);
        when(service.query(any(TraceabilityQuery.class))).thenReturn(
                new TraceabilityProjection(List.of(), List.of(), 0, List.of(),
                        Instant.parse("2026-09-04T00:00:00Z"), null, "request-s7"));
        TraceabilityController controller = new TraceabilityController(service,
                new TrustedFactsQueryContextFactory());

        controller.query("sales_order", null, ENTITY_ID, null);

        var query = org.mockito.ArgumentCaptor.forClass(TraceabilityQuery.class);
        verify(service).query(query.capture());
        assertEquals(TENANT, query.getValue().context().tenantId());
        assertEquals(ENTITY_ID, query.getValue().entityId());
        assertTrueHasPreAuthorize(TraceabilityController.class, "query");
    }

    private static void bindContext(Set<String> permissions) {
        RequestContext context = new RequestContext();
        context.setTenantId(TENANT);
        context.setUserId(UUID.randomUUID());
        context.setPermissions(permissions);
        context.setRequestId("request-s7");
        RequestContextHolder.setContext(context);
    }

    private static void assertTrueHasPreAuthorize(Class<?> type, String methodName) {
        assertEquals(1, java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
                .count());
    }
}
