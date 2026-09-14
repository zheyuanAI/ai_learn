package com.ailearn.platform.core.ai.controller;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiDelegatedUserContext;
import com.ailearn.platform.core.ai.application.AiToolExecutor;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.application.OpenWebUiInvocationContextStore;
import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.dto.AiTraceToolRequest;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Open WebUI 工具入口先验证独立服务密钥，再使用短期关联恢复用户。 */
class OpenWebUiToolControllerTest {

    @Test
    void shouldRejectWrongServiceSecretBeforeResolvingUser() {
        AiProperties properties = new AiProperties();
        properties.setToolServiceSecret("expected-secret");
        OpenWebUiInvocationContextStore store = mock(OpenWebUiInvocationContextStore.class);
        OpenWebUiToolController controller = new OpenWebUiToolController(
                store, mock(AiToolExecutor.class), properties, new AiDelegatedUserContext());

        assertThrows(AuthenticationException.class, () -> controller.queryTrace(
                "wrong-secret", "chat-1", "message-1",
                new AiTraceToolRequest("SALES_ORDER", UUID.randomUUID().toString())));
    }

    @Test
    void shouldExecuteOnlyWithResolvedTrustedContext() {
        AiProperties properties = new AiProperties();
        properties.setToolServiceSecret("service-secret");
        UUID aiSessionId = UUID.randomUUID();
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of("trace:chain:view"), ZoneId.of("Asia/Shanghai"), "fingerprint", "request-3");
        OpenWebUiInvocationContextStore store = mock(OpenWebUiInvocationContextStore.class);
        when(store.recordStarted("chat-3", "message-3", "queryTrace")).thenReturn("call-3");
        when(store.resolve("chat-3", "message-3", "queryTrace"))
                .thenReturn(new OpenWebUiInvocationContextStore.ResolvedInvocation(
                        context, aiSessionId, Set.of("querytrace")));
        AiToolResult expected = new AiToolResult("queryTrace", Map.of("status", "Approved"),
                "追溯事实", "更新至当前", List.of(), AiToolStatus.Success);
        AiToolExecutor executor = mock(AiToolExecutor.class);
        when(executor.execute(eq(context), eq(aiSessionId), eq("queryTrace"), any(),
                eq(Set.of("querytrace")))).thenReturn(expected);
        OpenWebUiToolController controller = new OpenWebUiToolController(
                store, executor, properties, new AiDelegatedUserContext());

        AiToolResult actual = controller.queryTrace(
                "service-secret", "chat-3", "message-3",
                new AiTraceToolRequest("SALES_ORDER", context.userId().toString()));

        assertEquals(expected, actual);
        verify(executor).execute(context, aiSessionId, "queryTrace", Map.of(
                "entity_type", "SALES_ORDER", "entity_id", context.userId().toString()),
                Set.of("querytrace"));
        verify(store).recordResult("chat-3", "message-3", "call-3", expected);
    }
}
