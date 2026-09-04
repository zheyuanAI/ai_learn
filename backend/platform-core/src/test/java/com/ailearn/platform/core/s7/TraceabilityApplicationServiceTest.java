package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.dto.TraceabilityProjection;
import com.ailearn.platform.core.traceability.dto.TraceabilityQuery;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceabilityApplicationServiceTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void shouldBuildRealSourceLinksAndHideUnauthorizedOrCrossTenantNodes() {
        S7FactsFake facts = new S7FactsFake();
        UUID orderId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID hiddenId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();
        facts.putTrace("sales", "sales_order", orderId, new TraceFacts(List.of(
                node(orderId, "sales_order", "sales:order:view", true, TENANT),
                node(workOrderId, "work_order", "manufacturing:work-order:view", true, TENANT),
                node(hiddenId, "alarm", "iot:alarm:view", true, TENANT),
                node(foreignId, "inventory_transaction", "inventory:transaction:view", true, OTHER_TENANT)),
                List.of(new TraceLink("sales_order", orderId, "work_order", workOrderId, "source_work_order"),
                        new TraceLink("sales_order", orderId, "alarm", hiddenId, "alarm_context")),
                Instant.parse("2026-09-04T00:00:00Z"), "sales事实"));
        facts.setUnavailable("inventory", true);
        TraceabilityApplicationService service = traceService(facts);
        var context = S7TestSupport.context(TENANT, "perm-trace", "trace:chain:view",
                "sales:order:view", "manufacturing:work-order:view");

        TraceabilityProjection result = service.query(new TraceabilityQuery(context, "sales_order", orderId));

        assertEquals(2, result.nodes().size());
        assertEquals(1, result.links().size());
        assertEquals(2, result.hiddenNodeCount());
        assertTrue(result.missingSources().contains("inventory"));
        assertEquals("request-s7", result.requestId());
    }

    @Test
    void shouldRejectTraceQueryWithoutChainPermission() {
        S7FactsFake facts = new S7FactsFake();
        TraceabilityApplicationService service = traceService(facts);
        var context = S7TestSupport.context(TENANT, "perm-none");
        assertEquals("GIS_AUTH_001", assertThrows(GisException.class,
                () -> service.query(new TraceabilityQuery(context, "sales_order", UUID.randomUUID())))
                .getBusinessCode());
    }

    @Test
    void shouldKeepLegacyAiTracePermissionReadableDuringPermissionCodeMigration() {
        S7FactsFake facts = new S7FactsFake();
        UUID orderId = UUID.randomUUID();
        facts.putTrace("sales", "sales_order", orderId, new TraceFacts(List.of(
                node(orderId, "sales_order", "sales:order:view", true, TENANT)), List.of(),
                Instant.parse("2026-09-04T00:00:00Z"), "sales事实"));
        TraceabilityApplicationService service = traceService(facts);
        var context = S7TestSupport.context(TENANT, "perm-legacy", "ai:trace:view", "sales:order:view");

        TraceabilityProjection result = service.query(new TraceabilityQuery(context, "sales_order", orderId));

        assertEquals(1, result.nodes().size());
    }

    private static TraceabilityApplicationService traceService(S7FactsFake facts) {
        return new TraceabilityApplicationService(facts, facts, facts, facts, facts, facts,
                java.time.Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneId.of("UTC")));
    }

    private static TraceNode node(UUID id, String type, String permission, boolean complete, UUID tenant) {
        return new TraceNode(tenant, type, id, type, "ACTIVE", permission,
                Instant.parse("2026-09-04T00:00:00Z"), complete);
    }
}
