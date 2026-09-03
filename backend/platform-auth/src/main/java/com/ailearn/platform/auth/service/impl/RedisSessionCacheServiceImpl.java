package com.ailearn.platform.auth.service.impl;

import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于 Redis 的用户会话状态与权限菜单缓存服务实现类。
 * <p>
 * 硬依赖 Redis 进行单账号单会话控制与缓存管理；异常时直接抛出 {@link ServiceUnavailableException}，
 * 严禁在生产路径中向内存缓存或数据库进行未经授权的兜底。
 * </p>
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "auth.session-cache", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisSessionCacheServiceImpl implements SessionCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionCacheServiceImpl.class);

    private static final String KEY_PREFIX_SESSION = "auth:session:";
    private static final String KEY_PREFIX_PERMS = "auth:perms:";
    private static final String KEY_PREFIX_MENUS = "auth:menus:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisSessionCacheServiceImpl(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String buildSessionKey(UUID tenantId, UUID userId) {
        return KEY_PREFIX_SESSION + tenantId + ":" + userId;
    }

    private String buildPermsKey(UUID tenantId, UUID userId) {
        return KEY_PREFIX_PERMS + tenantId + ":" + userId;
    }

    private String buildMenusKey(UUID tenantId, UUID userId) {
        return KEY_PREFIX_MENUS + tenantId + ":" + userId;
    }

    @Override
    public boolean isRedisAvailable() {
        try {
            String ping = redisTemplate.execute((RedisConnection connection) -> connection.ping());
            return "PONG".equalsIgnoreCase(ping);
        } catch (Exception e) {
            log.warn("[Redis健康探测失败] error={}", e.getMessage());
            return false;
        }
    }

    @Override
    public void saveActiveSession(UUID tenantId, UUID userId, String jti, Duration ttl) {
        try {
            String key = buildSessionKey(tenantId, userId);
            redisTemplate.opsForValue().set(key, jti, ttl);
            log.debug("[Redis保存活跃会话] key={}, jti={}, ttl={}", key, jti, ttl);
        } catch (Exception e) {
            log.error("[Redis保存会话失败，禁止内存兜底] key={}, error={}", buildSessionKey(tenantId, userId), e.getMessage());
            throw new ServiceUnavailableException("会话服务暂时不可用，请稍后重试", e);
        }
    }

    @Override
    public String getActiveSessionJti(UUID tenantId, UUID userId) {
        try {
            String key = buildSessionKey(tenantId, userId);
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("[Redis获取会话失败] key={}, error={}", buildSessionKey(tenantId, userId), e.getMessage());
            throw new ServiceUnavailableException("会话服务暂时不可用，请稍后重试", e);
        }
    }

    @Override
    public void removeActiveSession(UUID tenantId, UUID userId) {
        try {
            String key = buildSessionKey(tenantId, userId);
            redisTemplate.delete(key);
            log.debug("[Redis清除活跃会话] key={}", key);
        } catch (Exception e) {
            log.warn("[Redis删除会话异常] error={}", e.getMessage());
        }
    }

    @Override
    public Set<String> getCachedPermissions(UUID tenantId, UUID userId) {
        try {
            String key = buildPermsKey(tenantId, userId);
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<Set<String>>() {});
            }
        } catch (Exception e) {
            log.warn("[Redis读取权限缓存异常] error={}", e.getMessage());
        }
        return null;
    }

    @Override
    public void cachePermissions(UUID tenantId, UUID userId, Set<String> permissions, Duration ttl) {
        try {
            String key = buildPermsKey(tenantId, userId);
            String json = objectMapper.writeValueAsString(permissions);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("[Redis写入权限缓存异常] error={}", e.getMessage());
        }
    }

    @Override
    public List<MenuNodeVo> getCachedMenus(UUID tenantId, UUID userId) {
        try {
            String key = buildMenusKey(tenantId, userId);
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, new TypeReference<List<MenuNodeVo>>() {});
            }
        } catch (Exception e) {
            log.warn("[Redis读取菜单缓存异常] error={}", e.getMessage());
        }
        return null;
    }

    @Override
    public void cacheMenus(UUID tenantId, UUID userId, List<MenuNodeVo> menus, Duration ttl) {
        try {
            String key = buildMenusKey(tenantId, userId);
            String json = objectMapper.writeValueAsString(menus);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("[Redis写入菜单缓存异常] error={}", e.getMessage());
        }
    }

    @Override
    public void evictUserAuthCache(UUID tenantId, UUID userId) {
        try {
            String permsKey = buildPermsKey(tenantId, userId);
            String menusKey = buildMenusKey(tenantId, userId);
            redisTemplate.delete(List.of(permsKey, menusKey));
            log.debug("[Redis主动失效用户权限与菜单缓存] tenantId={}, userId={}", tenantId, userId);
        } catch (Exception e) {
            log.warn("[Redis清除权限菜单缓存异常] error={}", e.getMessage());
        }
    }

    @Override
    public void clearAll() {
        try {
            Set<String> keys = redisTemplate.keys("auth:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[Redis清除全部缓存异常] error={}", e.getMessage());
        }
    }
}
