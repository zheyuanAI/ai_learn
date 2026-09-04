package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Redis 权限上下文读取器 Fail-Closed 测试")
class RedisPermissionContextReaderTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisPermissionContextReader reader =
            new RedisPermissionContextReader(redisTemplate, new ObjectMapper());
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("JSON 数组解析为权限集合")
    void readsJsonArray() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:perms:" + tenantId + ":" + userId))
                .thenReturn("[\"inventory:balance:view\",\"sales:order:create\"]");

        assertEquals(Set.of("inventory:balance:view", "sales:order:create"),
                reader.readPermissions(tenantId, userId));
    }

    @Test
    @DisplayName("空权限数组保留为已认证但无权限")
    void emptyArrayMeansNoPermission() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:perms:" + tenantId + ":" + userId)).thenReturn("[]");

        assertEquals(Set.of(), reader.readPermissions(tenantId, userId));
    }

    @Test
    @DisplayName("缓存未命中、非数组和 Redis 异常均返回 503 异常")
    void failClosedForMissingMalformedAndRedisFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = "auth:perms:" + tenantId + ":" + userId;

        when(valueOperations.get(key)).thenReturn(null);
        assertThrows(ServiceUnavailableException.class, () -> reader.readPermissions(tenantId, userId));

        when(valueOperations.get(key)).thenReturn("{\"permission\":\"inventory:balance:view\"}");
        assertThrows(ServiceUnavailableException.class, () -> reader.readPermissions(tenantId, userId));

        when(valueOperations.get(key)).thenThrow(new IllegalStateException("redis timeout"));
        assertThrows(ServiceUnavailableException.class, () -> reader.readPermissions(tenantId, userId));
    }
}
