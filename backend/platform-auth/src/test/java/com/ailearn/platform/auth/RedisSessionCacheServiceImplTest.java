package com.ailearn.platform.auth;

import com.ailearn.platform.auth.service.impl.RedisSessionCacheServiceImpl;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 会话与权限缓存实现的严格错误语义单元测试。
 */
class RedisSessionCacheServiceImplTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisSessionCacheServiceImpl cacheService =
            new RedisSessionCacheServiceImpl(redisTemplate, new ObjectMapper());

    private final UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("c0000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 验证会话 TTL 可被读取并转换为 Duration；入参为模拟 Redis TTL，出参为剩余时长。
     */
    @Test
    @DisplayName("会话 TTL 应按毫秒读取")
    void shouldReadActiveSessionTtl() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(12_345L);

        Duration ttl = cacheService.getActiveSessionTtl(tenantId, userId);

        assertNotNull(ttl);
        assertEquals(Duration.ofMillis(12_345L), ttl);
    }

    /**
     * 验证不存在的 Redis 会话返回缓存未命中，而不是伪造授权生命周期。
     */
    @Test
    @DisplayName("不存在的会话 TTL 应返回未命中")
    void shouldReturnNullWhenSessionDoesNotExist() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(-2L);

        assertNull(cacheService.getActiveSessionTtl(tenantId, userId));
    }

    /**
     * 验证 Redis 读取会话 TTL 失败时直接抛出 503，禁止静默降级。
     */
    @Test
    @DisplayName("读取会话 TTL 失败必须 Fail-Closed")
    void shouldFailClosedWhenReadingSessionTtlFails() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(ServiceUnavailableException.class,
                () -> cacheService.getActiveSessionTtl(tenantId, userId));
    }

    /**
     * 验证权限 JSON 能正常读取；入参为 Redis 数组字符串，出参为权限集合。
     */
    @Test
    @DisplayName("权限缓存 JSON 应解析为权限集合")
    void shouldReadPermissionSnapshot() {
        when(valueOperations.get(anyString())).thenReturn("[\"inventory:balance:view\",\"inventory:move\"]");

        Set<String> permissions = cacheService.getCachedPermissions(tenantId, userId);

        assertEquals(Set.of("inventory:balance:view", "inventory:move"), permissions);
    }

    /**
     * 验证非数组 JSON 不被宽松反序列化为权限集合，入参为非法对象载荷，出参为 503 异常。
     */
    @Test
    @DisplayName("非数组权限缓存必须被拒绝")
    void shouldRejectNonArrayPermissionSnapshot() {
        when(valueOperations.get(anyString())).thenReturn("{\"permission\":\"inventory:move\"}");

        assertThrows(ServiceUnavailableException.class,
                () -> cacheService.getCachedPermissions(tenantId, userId));
    }

    /**
     * 验证权限缓存读取异常返回 503，避免数据库回源或使用客户端伪造权限。
     */
    @Test
    @DisplayName("权限缓存读取失败必须 Fail-Closed")
    void shouldFailClosedWhenReadingPermissionSnapshotFails() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(ServiceUnavailableException.class,
                () -> cacheService.getCachedPermissions(tenantId, userId));
    }

    /**
     * 验证权限缓存写入依赖 Redis 成功；入参为有效权限与 TTL，Redis 异常时出参为 503 异常。
     */
    @Test
    @DisplayName("权限缓存写入失败必须 Fail-Closed")
    void shouldFailClosedWhenWritingPermissionSnapshotFails() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThrows(ServiceUnavailableException.class,
                () -> cacheService.cachePermissions(tenantId, userId,
                        Set.of("inventory:balance:view"), Duration.ofMinutes(5)));
    }

    /**
     * 验证权限缓存删除失败同样暴露 503，避免业务误以为旧授权已经失效。
     */
    @Test
    @DisplayName("权限缓存删除失败必须 Fail-Closed")
    void shouldFailClosedWhenEvictingPermissionSnapshotFails() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisTemplate).delete(anyCollection());

        assertThrows(ServiceUnavailableException.class,
                () -> cacheService.evictUserAuthCache(tenantId, userId));
    }

    /**
     * 验证无效权限缓存 TTL 被拒绝，避免生成永不过期或已过期的授权快照。
     */
    @Test
    @DisplayName("权限缓存必须拒绝无效 TTL")
    void shouldRejectInvalidPermissionCacheTtl() {
        assertThrows(ServiceUnavailableException.class,
                () -> cacheService.cachePermissions(tenantId, userId, Set.of(), Duration.ZERO));
    }
}
