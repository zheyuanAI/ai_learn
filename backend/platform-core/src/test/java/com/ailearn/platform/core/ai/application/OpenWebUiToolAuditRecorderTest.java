package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.domain.ToolAuditEntry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gateway 业务 API 观察结果必须落入原有 AI 工具审计，并忽略尚未结束的 Running 事件。 */
class OpenWebUiToolAuditRecorderTest {

    @Test
    void shouldPersistFinishedBusinessApiCall() {
        List<ToolAuditEntry> saved = new ArrayList<>();
        AiProperties properties = new AiProperties();
        properties.setOpenWebuiModel("wms-assistant");
        OpenWebUiToolAuditRecorder recorder = new OpenWebUiToolAuditRecorder(saved::add, properties,
                Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC));
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("sales:order:view"), ZoneId.of("Asia/Shanghai"), "fp", "request-1");
        String callId = UUID.randomUUID().toString();

        recorder.record(context, UUID.randomUUID(), List.of(
                new OpenWebUiInvocationContextStore.ToolObservation("salesOrderDetail", "", "",
                        "Running", callId, "GET /api/sales-orders/{id}", "", 0),
                new OpenWebUiInvocationContextStore.ToolObservation("salesOrderDetail",
                        "GET /api/sales-orders/{id} -> HTTP 200", "", "Success", callId,
                        "GET /api/sales-orders/{id}", "HTTP 200", 24)));

        assertEquals(1, saved.size());
        assertEquals("salesOrderDetail", saved.getFirst().toolName());
        assertEquals(AiToolStatus.Success, saved.getFirst().status());
        assertEquals(24, saved.getFirst().durationMs());
        assertEquals("wms-assistant", saved.getFirst().modelId());
    }
}
