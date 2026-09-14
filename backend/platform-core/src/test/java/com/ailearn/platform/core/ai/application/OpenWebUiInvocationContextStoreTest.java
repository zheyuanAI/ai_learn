package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.exception.AiException;
import com.ailearn.platform.shared.security.PermissionContextReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Open WebUI 工具回调必须重新校验当前会话和权限，关联 ID 本身不能充当凭据。 */
class OpenWebUiInvocationContextStoreTest {

    @Test
    void shouldResolveLatestPermissionsOnlyWhenSessionJtiStillMatches() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID aiSessionId = UUID.randomUUID();
        AtomicReference<String> invocationJson = new AtomicReference<>();
        AtomicReference<String> activeJti = new AtomicReference<>("jti-1");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (!key.endsWith(":calls")) {
                invocationJson.set(invocation.getArgument(1));
            }
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenReturn(1L);
        when(values.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.startsWith("auth:session:") ? activeJti.get() : invocationJson.get();
        });
        PermissionContextReader permissionReader = mock(PermissionContextReader.class);
        when(permissionReader.readPermissions(tenantId, userId))
                .thenReturn(Set.of("trace:chain:view", "ai:chat:query"));
        AiProperties properties = new AiProperties();
        properties.setInvocationContextTtl(Duration.ofMinutes(5));
        OpenWebUiInvocationContextStore store = new OpenWebUiInvocationContextStore(
                redis, permissionReader, new ObjectMapper(), properties);
        AiRequestContext original = new AiRequestContext(tenantId, userId, "jti-1",
                Set.of("trace:chain:view"), ZoneId.of("Asia/Shanghai"), "old", "request-1");

        store.bind("chat-1", "message-1", aiSessionId, original, Set.of("queryTrace"));
        OpenWebUiInvocationContextStore.ResolvedInvocation resolved = store.resolve(
                "chat-1", "message-1", "QUERYTRACE");

        assertEquals(aiSessionId, resolved.aiSessionId());
        assertEquals(Set.of("trace:chain:view", "ai:chat:query"), resolved.context().permissions());
        activeJti.set("jti-2");
        assertThrows(AiException.class, () -> store.resolve("chat-1", "message-1", "queryTrace"));
    }

    @Test
    void shouldCarryRequestScopedAllowlistToAuditedExecutor() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AtomicReference<String> invocationJson = new AtomicReference<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (!key.endsWith(":calls")) {
                invocationJson.set(invocation.getArgument(1));
            }
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(anyString())).thenReturn(1L);
        when(values.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.startsWith("auth:session:") ? "jti-1" : invocationJson.get();
        });
        PermissionContextReader permissionReader = mock(PermissionContextReader.class);
        when(permissionReader.readPermissions(tenantId, userId)).thenReturn(Set.of());
        AiProperties properties = new AiProperties();
        OpenWebUiInvocationContextStore store = new OpenWebUiInvocationContextStore(
                redis, permissionReader, new ObjectMapper(), properties);
        AiRequestContext context = new AiRequestContext(tenantId, userId, "jti-1", Set.of(),
                ZoneId.of("Asia/Shanghai"), "old", "request-2");

        store.bind("chat-2", "message-2", UUID.randomUUID(), context, Set.of("queryTrace"));

        OpenWebUiInvocationContextStore.ResolvedInvocation resolved =
                store.resolve("chat-2", "message-2", "queryOperationAudit");

        assertEquals(Set.of("querytrace"), resolved.allowedTools());
    }

    @Test
    void shouldStoreOnlyToolResultMetadataForFinalSummary() {
        AtomicReference<String> resultJson = new AtomicReference<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ListOperations<String, String> lists = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        doAnswer(invocation -> {
            resultJson.set(invocation.getArgument(1));
            return 1L;
        }).when(lists).rightPush(anyString(), anyString());
        when(lists.range(anyString(), eq(0L), eq(-1L)))
                .thenAnswer(invocation -> List.of(resultJson.get()));
        AiProperties properties = new AiProperties();
        OpenWebUiInvocationContextStore store = new OpenWebUiInvocationContextStore(
                redis, mock(PermissionContextReader.class), new ObjectMapper(), properties);
        AiToolResult result = new AiToolResult("querySalesOrderStatus", Map.of("status", "Completed"),
                "sales facts", "数据更新至当前", List.of(), AiToolStatus.Success);

        store.recordResult("chat-result", "message-result", result);
        List<OpenWebUiInvocationContextStore.ToolObservation> observations =
                store.results("chat-result", "message-result");

        assertEquals(1, observations.size());
        assertEquals("querySalesOrderStatus", observations.getFirst().toolName());
        assertEquals("sales facts", observations.getFirst().sourceSummary());
    }

    @Test
    void shouldRejectCallsAboveServerSideMaximum() {
        AtomicReference<String> invocationJson = new AtomicReference<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (!key.endsWith(":calls")) {
                invocationJson.set(invocation.getArgument(1));
            }
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(invocation -> invocationJson.get());
        when(values.increment(anyString())).thenReturn(3L);
        AiProperties properties = new AiProperties();
        properties.setMaxToolCalls(2);
        OpenWebUiInvocationContextStore store = new OpenWebUiInvocationContextStore(
                redis, mock(PermissionContextReader.class), new ObjectMapper(), properties);
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti-1",
                Set.of("trace:chain:view"), ZoneId.of("Asia/Shanghai"), "old", "request-limit");
        store.bind("chat-limit", "message-limit", UUID.randomUUID(), context, Set.of("queryTrace"));

        assertThrows(AiException.class,
                () -> store.resolve("chat-limit", "message-limit", "queryTrace"));
    }
}
