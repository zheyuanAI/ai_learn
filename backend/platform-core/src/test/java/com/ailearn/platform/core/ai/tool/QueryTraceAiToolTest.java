package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.dto.TraceabilityProjection;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** queryTrace AI 工具复用阶段 7 权限裁剪投影且不生成导航动作。 */
class QueryTraceAiToolTest {

    @Test
    void shouldReturnTraceProjectionWithoutNavigationActions() {
        UUID tenantId = UUID.randomUUID();
        UUID salesOrderId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        TraceabilityApplicationService service = mock(TraceabilityApplicationService.class);
        when(service.query(any())).thenReturn(new TraceabilityProjection(List.of(
                new TraceNode(tenantId, "SALES_ORDER", salesOrderId, "SO-001", "Approved",
                        "sales:order:view", Instant.parse("2026-09-12T00:00:00Z"), true),
                new TraceNode(tenantId, "WORK_ORDER", workOrderId, "WO-001", "Released",
                        "mes:workorder:view", Instant.parse("2026-09-12T00:01:00Z"), true)
        ), List.of(), 0, List.of(), Instant.parse("2026-09-12T00:02:00Z"),
                Instant.parse("2026-09-12T00:01:00Z"), "request-ai", false));
        QueryTraceAiTool tool = new QueryTraceAiTool(service);
        AiRequestContext context = new AiRequestContext(tenantId, UUID.randomUUID(), "jti",
                Set.of("trace:chain:view", "sales:order:view"), ZoneId.of("Asia/Shanghai"),
                "permission-fingerprint", "request-ai");

        AiToolResult result = tool.execute(context, Map.of(
                "entity_type", "SALES_ORDER", "entity_id", salesOrderId.toString()));

        assertEquals(2, ((TraceabilityProjection) result.data()).nodes().size());
        assertTrue(result.navigationActions().isEmpty());
    }

    @Test
    void shouldRejectUnknownArgumentsBeforeCallingTraceabilityService() {
        QueryTraceAiTool tool = new QueryTraceAiTool(mock(TraceabilityApplicationService.class));
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("trace:chain:view"), ZoneId.of("Asia/Shanghai"),
                "permission-fingerprint", "request-ai");

        assertThrows(IllegalArgumentException.class, () -> tool.execute(context,
                Map.of("entity_type", "SALES_ORDER", "entity_id", UUID.randomUUID().toString(),
                        "tenant_id", UUID.randomUUID().toString())));
    }

    @Test
    void shouldKeepToolRegisteredButFailClearlyWhenTraceabilitySourceIsDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<TraceabilityApplicationService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        QueryTraceAiTool tool = new QueryTraceAiTool(provider);
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("trace:chain:view"), ZoneId.of("Asia/Shanghai"),
                "permission-fingerprint", "request-ai");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> tool.execute(context,
                Map.of("entity_type", "SALES_ORDER", "entity_id", UUID.randomUUID().toString())));

        assertTrue(exception.getMessage().contains("追溯事实源未启用"));
    }
}
