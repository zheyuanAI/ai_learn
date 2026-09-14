package com.ailearn.platform.core.ai;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolRegistry;
import com.ailearn.platform.core.ai.application.AiToolResult;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AI 工具注册表只暴露服务端已注册且当前用户有权使用的工具。 */
class AiToolRegistryTest {
    private static final AiRequestContext CONTEXT = new AiRequestContext(
            UUID.randomUUID(), UUID.randomUUID(), "jti", Set.of("trace:chain:view"),
            ZoneId.of("Asia/Shanghai"), "permission-fingerprint", "request-ai");

    @Test
    void shouldFilterToolsByTrustedPermissionSnapshot() {
        AiToolRegistry registry = new AiToolRegistry(List.of(
                tool("queryTrace", "trace:chain:view"),
                tool("queryInventoryByProductAndWarehouse", "inv:balance:view")));

        assertEquals(List.of("queryTrace"), registry.availableToolNames(CONTEXT));
        assertTrue(registry.find("QUERYTRACE").isPresent());
        assertTrue(registry.find("unknownTool").isEmpty());
    }

    @Test
    void shouldRejectDuplicateToolNamesIgnoringCase() {
        assertThrows(IllegalStateException.class, () -> new AiToolRegistry(List.of(
                tool("queryTrace", "trace:chain:view"),
                tool("QUERYTRACE", "trace:chain:view"))));
    }

    @Test
    void shouldIntersectConfiguredToolsWithTrustedPermissions() {
        AiToolRegistry registry = new AiToolRegistry(List.of(
                tool("queryTrace", "trace:chain:view"),
                tool("queryInventoryByProductAndWarehouse", "inv:balance:view")),
                Set.of("queryTrace"));

        AiRequestContext allPermissions = new AiRequestContext(
                UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("trace:chain:view", "inv:balance:view"),
                ZoneId.of("Asia/Shanghai"), "permission-fingerprint", "request-ai-config");

        assertEquals(List.of("queryTrace"), registry.availableToolNames(allPermissions));
        assertTrue(registry.find("queryTrace").isPresent());
        assertTrue(registry.find("queryInventoryByProductAndWarehouse").isEmpty());
    }

    @Test
    void shouldRejectConfiguredToolOutsideCodeRegistry() {
        assertThrows(IllegalStateException.class, () -> new AiToolRegistry(
                List.of(tool("queryTrace", "trace:chain:view")),
                Set.of("deleteSalesOrder")));
    }

    @Test
    void writePermissionMustNotGrantAnyAiReadTool() {
        AiToolRegistry registry = new AiToolRegistry(List.of(
                tool("queryTrace", "trace:chain:view"),
                tool("queryInventoryByProductAndWarehouse", "inv:balance:view")));
        AiRequestContext writeOnly = new AiRequestContext(
                UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("sales:order:delete", "inv:balance:adjust", "auth:role:edit"),
                ZoneId.of("Asia/Shanghai"), "permission-fingerprint", "request-ai-write-only");

        assertTrue(registry.availableToolNames(writeOnly).isEmpty());
    }

    private static AiReadTool tool(String name, String permission) {
        return new AiReadTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Set<String> anyOfPermissions() {
                return Set.of(permission);
            }

            @Override
            public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
                throw new UnsupportedOperationException("测试无需执行工具");
            }
        };
    }
}
