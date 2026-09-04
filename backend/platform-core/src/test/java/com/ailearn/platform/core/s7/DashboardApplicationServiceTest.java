package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import com.ailearn.platform.core.dashboard.ports.InMemoryDashboardCache;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardApplicationServiceTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant START = Instant.parse("2026-09-04T01:00:00Z");

    @Test
    void shouldReturnAllSevenSummaryTypesWithActualTenantTimezoneRange() {
        S7FactsFake facts = new S7FactsFake();
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));
        DashboardApplicationService service = dashboardService(facts, clock);
        var context = S7TestSupport.context(TENANT, "perm-a", "dashboard:view", "trace:chain:view");

        for (DashboardSummaryType type : DashboardSummaryType.values()) {
            DashboardSummaryProjection result = service.query(type, new DashboardQuery(context, "today", Map.of()));
            assertEquals(type, result.summaryType());
            assertFalse(result.metrics().isEmpty());
            assertEquals(Instant.parse("2026-09-03T16:00:00Z"), result.timeRange().from());
            assertEquals(START, result.timeRange().to());
            assertFalse(result.stale());
            assertEquals("request-s7", result.requestId());
        }
    }

    @Test
    void shouldKeepFreshCacheThenReturnStaleAndFinallyFailWithoutZeroReplacement() {
        S7FactsFake facts = new S7FactsFake();
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));
        DashboardApplicationService service = dashboardService(facts, clock);
        var context = S7TestSupport.context(TENANT, "perm-a", "dashboard:view");
        DashboardQuery query = new DashboardQuery(context, "7d", Map.of());

        DashboardSummaryProjection first = service.query(DashboardSummaryType.INVENTORY, query);
        int callsAfterFirst = facts.callCount();
        DashboardSummaryProjection freshReplay = service.query(DashboardSummaryType.INVENTORY, query);
        assertEquals(callsAfterFirst, facts.callCount());
        assertFalse(freshReplay.stale());

        clock.advance(Duration.ofSeconds(61));
        facts.setUnavailable("inventory", true);
        DashboardSummaryProjection stale = service.query(DashboardSummaryType.INVENTORY, query);
        assertTrue(stale.stale());
        assertEquals(first.generatedAt(), stale.staleSince());
        assertEquals(first.metrics(), stale.metrics());

        clock.advance(Duration.ofMinutes(9).plusSeconds(1));
        GisException unavailable = assertThrows(GisException.class,
                () -> service.query(DashboardSummaryType.INVENTORY, query));
        assertEquals("GIS_QUERY_002", unavailable.getBusinessCode());
    }

    @Test
    void shouldSeparateCacheByPermissionFingerprintAndRejectUnsupportedRangeOrTenantFilter() {
        S7FactsFake facts = new S7FactsFake();
        MutableClock clock = new MutableClock(START, ZoneId.of("UTC"));
        DashboardApplicationService service = dashboardService(facts, clock);
        var firstContext = S7TestSupport.context(TENANT, "perm-a", "dashboard:view");
        var secondContext = S7TestSupport.context(TENANT, "perm-b", "dashboard:view");
        service.query(DashboardSummaryType.INVENTORY, new DashboardQuery(firstContext, "today", Map.of()));
        int callsAfterFirstFingerprint = facts.callCount();
        service.query(DashboardSummaryType.INVENTORY, new DashboardQuery(secondContext, "today", Map.of()));
        assertTrue(facts.callCount() > callsAfterFirstFingerprint);

        GisException rangeError = assertThrows(GisException.class,
                () -> service.query(DashboardSummaryType.INVENTORY, new DashboardQuery(firstContext, "90d", Map.of())));
        assertEquals("GIS_QUERY_001", rangeError.getBusinessCode());
        GisException tenantError = assertThrows(GisException.class,
                () -> service.query(DashboardSummaryType.INVENTORY,
                        new DashboardQuery(firstContext, "today", Map.of("warehouse_id", UUID.randomUUID().toString()))));
        assertEquals("GIS_TENANT_001", tenantError.getBusinessCode());
    }

    @Test
    void shouldFailClearlyWhenThereIsNoPreviousSuccessfulResult() {
        S7FactsFake facts = new S7FactsFake();
        facts.setUnavailable("quality", true);
        DashboardApplicationService service = dashboardService(facts,
                new MutableClock(START, ZoneId.of("UTC")));
        var context = S7TestSupport.context(TENANT, "perm-a", "dashboard:view");
        GisException unavailable = assertThrows(GisException.class,
                () -> service.query(DashboardSummaryType.QUALITY,
                        new DashboardQuery(context, "30d", Map.of())));
        assertEquals("GIS_QUERY_002", unavailable.getBusinessCode());
    }

    private static DashboardApplicationService dashboardService(S7FactsFake facts, MutableClock clock) {
        TraceabilityApplicationService traceability = new TraceabilityApplicationService(
                facts, facts, facts, facts, facts, facts, clock);
        return new DashboardApplicationService(facts, facts, facts, facts, facts, facts,
                traceability, new InMemoryDashboardCache(), clock);
    }
}
