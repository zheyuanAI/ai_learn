package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditEntry;
import com.ailearn.platform.core.operationaudit.domain.OperationAuditResult;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI 业务操作历史工具的租户、权限、参数和脱敏测试。 */
class QueryOperationAuditAiToolTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

    /** 查询只能使用可信上下文租户，并向模型隐藏原始会话和幂等键摘要。 */
    @Test
    void shouldQueryTrustedTenantAndReturnSanitizedTimeline() {
        OperationAuditApplicationService service = mock(OperationAuditApplicationService.class);
        QueryOperationAuditAiTool tool = new QueryOperationAuditAiTool(service);
        OffsetDateTime occurredAt = OffsetDateTime.of(2026, 9, 12, 2, 0, 0, 0, ZoneOffset.UTC);
        OperationAuditEntry entry = new OperationAuditEntry(UUID.randomUUID(), TENANT_ID, "USER", USER_ID,
                "sales-user", "raw-session-jti", "request-1", "sales:order:approve", "SALES_ORDER",
                ORDER_ID, "SO-001", "Submitted", "Approved", "审核销售订单", "status",
                OperationAuditResult.SUCCESS, null, null, "secret-hash", occurredAt);
        when(service.query(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(entry));

        AiToolResult result = tool.execute(context(Set.of("sales:order:view")), Map.of(
                "entity_type", "SALES_ORDER", "entity_id", ORDER_ID.toString(), "limit", 20));

        ArgumentCaptor<OperationAuditQuery> captor = ArgumentCaptor.forClass(OperationAuditQuery.class);
        verify(service).query(captor.capture());
        assertEquals(TENANT_ID, captor.getValue().tenantId());
        assertEquals(20, captor.getValue().limit());
        assertTrue(result.timeRangeSummary().contains(occurredAt.toString()));
        assertTrue(result.navigationActions().isEmpty());
        String data = result.data().toString();
        assertTrue(data.contains("sales-user"));
        assertTrue(data.contains("session-"));
        assertFalse(data.contains("raw-session-jti"));
        assertFalse(data.contains("secret-hash"));
    }

    /** 无销售查看权限时工具不可用，超出上限的查询参数也必须拒绝。 */
    @Test
    void shouldRequireSalesPermissionAndRejectExcessiveLimit() {
        QueryOperationAuditAiTool tool = new QueryOperationAuditAiTool(
                mock(OperationAuditApplicationService.class));

        assertFalse(tool.isAllowed(context(Set.of("ai:chat:query"))));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(
                context(Set.of("sales:order:view")), Map.of(
                        "entity_type", "SALES_ORDER", "entity_id", ORDER_ID.toString(), "limit", 101)));
    }

    private AiRequestContext context(Set<String> permissions) {
        return new AiRequestContext(TENANT_ID, USER_ID, "ai-jti", permissions,
                ZoneId.of("Asia/Shanghai"), "permission-fingerprint", "request-ai");
    }
}
