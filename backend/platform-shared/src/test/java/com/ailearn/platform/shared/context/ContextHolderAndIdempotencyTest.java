package com.ailearn.platform.shared.context;

import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("上下文与幂等存储测试")
class ContextHolderAndIdempotencyTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @DisplayName("测试 UserContextHolder 与 TenantContextHolder")
    void testContextHolders() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        RequestContext context = new RequestContext();
        context.setTenantId(tenantId);
        context.setUserId(userId);
        context.setUsername("zhangsan");
        context.setRoles(Set.of("SALES"));
        context.setPermissions(Set.of("sales:order:create"));

        RequestContextHolder.setContext(context);

        assertEquals(tenantId, TenantContextHolder.getTenantId());
        assertEquals(tenantId, TenantContextHolder.requireTenantId());

        assertEquals(userId, UserContextHolder.getUserId());
        assertEquals(userId, UserContextHolder.requireUserId());
        assertEquals("zhangsan", UserContextHolder.getUsername());
        assertTrue(UserContextHolder.hasRole("SALES"));
        assertFalse(UserContextHolder.hasRole("ADMIN"));
        assertTrue(UserContextHolder.hasPermission("sales:order:create"));
        assertTrue(UserContextHolder.isAuthenticated());
    }

    @Test
    @DisplayName("测试 InMemoryIdempotencyStorage 幂等防重流程")
    void testIdempotencyStorage() {
        IdempotencyStorage storage = new InMemoryIdempotencyStorage();
        UUID tenantId = UUID.randomUUID();
        String idempotencyKey = "order-create-key-001";
        String requestHash = "hash-abc-123";

        // 第一次尝试加锁成功
        boolean firstAcquire = storage.tryAcquire(idempotencyKey, tenantId, Duration.ofMinutes(5), requestHash);
        assertTrue(firstAcquire);

        // 重复提交相同 key，应返回 false
        boolean secondAcquire = storage.tryAcquire(idempotencyKey, tenantId, Duration.ofMinutes(5), requestHash);
        assertFalse(secondAcquire);

        // 检查状态为 PENDING
        Optional<IdempotentRecord> recordOpt = storage.getRecord(idempotencyKey, tenantId);
        assertTrue(recordOpt.isPresent());
        assertEquals(IdempotentRecord.Status.PENDING, recordOpt.get().getStatus());

        // 完成执行
        String responseBody = "{\"code\":200,\"message\":\"创建成功\"}";
        storage.complete(idempotencyKey, tenantId, responseBody, Duration.ofMinutes(5));

        // 检查状态为 SUCCESS 并包含响应体
        Optional<IdempotentRecord> successOpt = storage.getRecord(idempotencyKey, tenantId);
        assertTrue(successOpt.isPresent());
        assertEquals(IdempotentRecord.Status.SUCCESS, successOpt.get().getStatus());
        assertEquals(responseBody, successOpt.get().getResponseBody());
    }
}
