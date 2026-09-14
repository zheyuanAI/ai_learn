package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.domain.ToolAuditEntry;
import com.ailearn.platform.core.ai.exception.AiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AI 工具执行器的权限、错误映射和全结果审计测试。 */
class AiToolExecutorTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

    @Test
    void shouldAuditSuccessfulReadOnlyToolCallAndRedactSensitiveArguments() {
        RecordingAuditStore auditStore = new RecordingAuditStore();
        AiToolExecutor executor = executor(auditStore, context -> true);

        AiToolResult result = executor.execute(context(Set.of("trace:chain:view")), null,
                "queryTrace", Map.of("entity_id", UUID.randomUUID().toString(), "api_key", "secret-value"));

        assertEquals(AiToolStatus.Success, result.status());
        assertEquals(1, auditStore.entries.size());
        ToolAuditEntry audit = auditStore.entries.getFirst();
        assertEquals(AiToolStatus.Success, audit.status());
        assertTrue(audit.inputSummary().contains("[REDACTED]"));
        assertTrue(!audit.inputSummary().contains("secret-value"));
    }

    @Test
    void shouldAuditUnknownAndUnauthorizedToolsAsDenied() {
        RecordingAuditStore auditStore = new RecordingAuditStore();
        AiToolExecutor executor = executor(auditStore, context -> false);

        assertThrows(AiException.class, () -> executor.execute(context(Set.of()), null,
                "unknownTool", Map.of("token", "do-not-store")));
        assertThrows(AiException.class, () -> executor.execute(context(Set.of()), null,
                "queryTrace", Map.of()));

        assertEquals(List.of(AiToolStatus.Denied, AiToolStatus.Denied),
                auditStore.entries.stream().map(ToolAuditEntry::status).toList());
        assertTrue(auditStore.entries.stream().allMatch(entry -> !entry.inputSummary().contains("do-not-store")));
    }

    @Test
    void shouldAuditToolOutsideRequestWhitelistAsDenied() {
        RecordingAuditStore auditStore = new RecordingAuditStore();
        AiToolExecutor executor = executor(auditStore, context -> true);

        assertThrows(AiException.class, () -> executor.execute(
                context(Set.of("trace:chain:view")), null, "queryTrace", Map.of(),
                Set.of("queryInventoryByProductAndWarehouse")));

        ToolAuditEntry audit = auditStore.entries.getFirst();
        assertEquals(AiToolStatus.Denied, audit.status());
        assertEquals("AI_TOOL_002", audit.errorCode());
        assertEquals("工具不在本次请求允许名单中", audit.errorReason());
    }

    @Test
    void shouldAuditOpenWebUiPresetInsteadOfDirectProviderModel() {
        RecordingAuditStore auditStore = new RecordingAuditStore();
        AiReadTool tool = successfulTool();
        AiProperties properties = new AiProperties();
        properties.setProvider("open-webui");
        properties.setModel("direct-model-must-not-be-used");
        properties.setOpenWebuiModel("wms-assistant");
        AiToolExecutor executor = new AiToolExecutor(new AiToolRegistry(List.of(tool)), auditStore,
                new AiAuditSanitizer(new ObjectMapper()), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        executor.execute(context(Set.of("trace:chain:view")), null, "queryTrace", Map.of());

        assertEquals("wms-assistant", auditStore.entries.getFirst().modelId());
    }

    @Test
    void shouldCancelTimedOutToolAndAuditTimeout() {
        RecordingAuditStore auditStore = new RecordingAuditStore();
        AiReadTool slowTool = new AiReadTool() {
            @Override
            public String name() {
                return "queryTrace";
            }

            @Override
            public Set<String> anyOfPermissions() {
                return Set.of("trace:chain:view");
            }

            @Override
            public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("测试查询被取消", exception);
                }
                throw new IllegalStateException("测试查询不应执行到这里");
            }
        };
        AiProperties properties = new AiProperties();
        properties.setToolTimeout(Duration.ofMillis(20));
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            AiToolExecutor executor = new AiToolExecutor(new AiToolRegistry(List.of(slowTool)), auditStore,
                    new AiAuditSanitizer(new ObjectMapper()), properties,
                    Clock.fixed(NOW, ZoneOffset.UTC), executorService, new AiDelegatedUserContext());

            AiException exception = assertThrows(AiException.class, () -> executor.execute(
                    context(Set.of("trace:chain:view")), null, "queryTrace", Map.of()));
            assertEquals("AI_TOOL_003", exception.getBusinessCode());
        }

        assertEquals(AiToolStatus.Timeout, auditStore.entries.getFirst().status());
        assertEquals("AI_TOOL_003", auditStore.entries.getFirst().errorCode());
    }

    private static AiToolExecutor executor(RecordingAuditStore store,
                                           java.util.function.Predicate<AiRequestContext> permission) {
        AiReadTool base = successfulTool();
        AiReadTool tool = new AiReadTool() {
            @Override
            public String name() {
                return base.name();
            }

            @Override
            public Set<String> anyOfPermissions() {
                return Set.of("trace:chain:view");
            }

            @Override
            public boolean isAllowed(AiRequestContext context) {
                return permission.test(context);
            }

            @Override
            public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
                return base.execute(context, arguments);
            }
        };
        AiProperties properties = new AiProperties();
        properties.setModel("deepseek-v4-flash");
        return new AiToolExecutor(new AiToolRegistry(List.of(tool)), store,
                new AiAuditSanitizer(new ObjectMapper()), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AiReadTool successfulTool() {
        return new AiReadTool() {
            @Override
            public String name() {
                return "queryTrace";
            }

            @Override
            public Set<String> anyOfPermissions() {
                return Set.of("trace:chain:view");
            }

            @Override
            public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
                return new AiToolResult(name(), Map.of("count", 1), "traceability", "today",
                        List.of(), AiToolStatus.Success);
            }
        };
    }

    private static AiRequestContext context(Set<String> permissions) {
        return new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti", permissions,
                ZoneId.of("Asia/Shanghai"), "permission-fingerprint", "request-ai");
    }

    private static final class RecordingAuditStore implements AiToolAuditStore {
        private final List<ToolAuditEntry> entries = new ArrayList<>();

        @Override
        public void save(ToolAuditEntry entry) {
            entries.add(entry);
        }
    }
}
