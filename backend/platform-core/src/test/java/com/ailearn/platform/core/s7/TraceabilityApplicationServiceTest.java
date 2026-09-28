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

    /** 用途：复现告警先返回工单引用的真实路径；后续 Core 事实应补全状态和名称，晚到引用不能降级。 */
    @Test
    void shouldUpgradeContextReferenceWithoutDowngradingAuthoritativeFact() {
        S7FactsFake facts = new S7FactsFake();
        UUID alarmId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        TraceNode reference = new TraceNode(TENANT, "work_order", workOrderId,
                "work-order-context", "CONTEXT_REFERENCE", "mes:workorder:view", null, false);
        TraceNode actual = new TraceNode(TENANT, "work_order", workOrderId,
                "SC-20260927-043", "Completed", "mes:workorder:view",
                Instant.parse("2026-09-27T06:20:00Z"), true);
        TraceNode execution = node(executionId, "operation_execution", "mes:execution:manage", true, TENANT);
        facts.putTrace("iot", "alarm", alarmId, new TraceFacts(List.of(
                node(alarmId, "alarm", "iot:alarm:view", true, TENANT),
                node(executionId, "operation_execution", "mes:execution:manage", false, TENANT), reference),
                List.of(new TraceLink("alarm", alarmId, "operation_execution", executionId, "business_context"),
                        new TraceLink("alarm", alarmId, "work_order", workOrderId, "business_context")),
                Instant.parse("2026-09-27T06:00:00Z"), "iot上下文引用"));
        // 修改用途：与真实适配器一样，先展开工序并遇到 complete=true 的通用工单引用，再直接查询工单。
        facts.putTrace("manufacturing", "operation_execution", executionId, new TraceFacts(List.of(execution,
                new TraceNode(TENANT, "work_order", workOrderId, "work-order-" + workOrderId,
                        "ACTIVE", "mes:workorder:view", execution.sourceUpdatedAt(), true)),
                List.of(), execution.sourceUpdatedAt(), "工序关联工单"));
        facts.putTrace("inventory", "work_order", workOrderId, new TraceFacts(List.of(
                new TraceNode(TENANT, "work_order", workOrderId, "work_order-" + workOrderId,
                        "RECORDED", "mes:workorder:view", null, true)), List.of(), null, "库存来源占位"));
        facts.putTrace("manufacturing", "work_order", workOrderId,
                new TraceFacts(List.of(actual), List.of(), actual.sourceUpdatedAt(), "真实工单"));
        facts.putTrace("iot", "work_order", workOrderId,
                new TraceFacts(List.of(reference), List.of(), null, "再次遇到引用"));
        var context = S7TestSupport.context(TENANT, "perm-complete", "trace:chain:view",
                "iot:alarm:view", "mes:workorder:view", "mes:execution:manage");

        TraceabilityProjection result = traceService(facts).query(new TraceabilityQuery(context, "alarm", alarmId));

        assertEquals(actual, result.nodes().stream().filter(value -> value.entityId().equals(workOrderId)).findFirst().orElseThrow());
        assertEquals(3, result.nodes().size());
        assertEquals(2, result.links().size());
        assertEquals(0, result.hiddenNodeCount());
        assertTrue(!result.truncated());
    }

    /** 用途：同标识的跨租户或无权限完整节点不得补全可见引用，避免升级时绕过原有裁剪。 */
    @Test
    void shouldNotUpgradeReferenceUsingUnauthorizedOrCrossTenantFact() {
        S7FactsFake facts = new S7FactsFake();
        UUID workOrderId = UUID.randomUUID();
        TraceNode reference = node(workOrderId, "work_order", "mes:workorder:view", false, TENANT);
        facts.putTrace("inventory", "work_order", workOrderId,
                new TraceFacts(List.of(reference), List.of(), null, "可见引用"));
        facts.putTrace("manufacturing", "work_order", workOrderId, new TraceFacts(List.of(
                node(workOrderId, "work_order", "mes:workorder:view", true, OTHER_TENANT),
                node(workOrderId, "work_order", "mes:execution:manage", true, TENANT)),
                List.of(), null, "不可见完整事实"));
        var context = S7TestSupport.context(TENANT, "perm-reference", "trace:chain:view", "mes:workorder:view");

        TraceabilityProjection result = traceService(facts).query(new TraceabilityQuery(context, "work_order", workOrderId));

        assertEquals(List.of(reference), result.nodes());
        assertEquals(1, result.hiddenNodeCount());
        assertTrue(result.links().isEmpty());
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
