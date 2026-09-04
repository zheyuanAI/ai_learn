package com.ailearn.platform.auth.service.impl;

import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

    /**
     * 读取 Redis 活跃会话键的剩余 TTL，供权限缓存重建复用同一生命周期。
     * 入参为租户与用户标识，返回剩余时长；键不存在返回 null，Redis 异常或无过期时间按 503 处理。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 剩余 TTL；会话不存在时返回 null
     */
    @Override
    public Duration getActiveSessionTtl(UUID tenantId, UUID userId) {
        String key = buildSessionKey(tenantId, userId);
        try {
            Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            if (ttlMillis == null || ttlMillis == -2L) {
                return null;
            }
            if (ttlMillis <= 0L) {
                throw new ServiceUnavailableException("会话 TTL 状态无效，请重新登录");
            }
            return Duration.ofMillis(ttlMillis);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("[Redis读取会话TTL失败] key={}, error={}", key, e.getMessage());
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
            log.error("[Redis删除会话失败] key={}, error={}", buildSessionKey(tenantId, userId), e.getMessage());
            throw new ServiceUnavailableException("会话服务暂时不可用，请稍后重试", e);
        }
    }

    @Override
    public Set<String> getCachedPermissions(UUID tenantId, UUID userId) {
        try {
            String key = buildPermsKey(tenantId, userId);
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            if (json.isBlank()) {
                throw new ServiceUnavailableException("权限缓存内容无效，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                throw new ServiceUnavailableException("权限缓存内容无效，请稍后重试");
            }
            Set<String> permissions = new LinkedHashSet<>();
            for (JsonNode permissionNode : root) {
                if (!permissionNode.isTextual() || permissionNode.asText().isBlank()) {
                    throw new ServiceUnavailableException("权限缓存内容无效，请稍后重试");
                }
                permissions.add(permissionNode.asText());
            }
            return Collections.unmodifiableSet(permissions);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("[Redis读取权限缓存失败] key={}, error={}", buildPermsKey(tenantId, userId), e.getMessage());
            throw new ServiceUnavailableException("权限服务暂时不可用，请稍后重试", e);
        }
    }

    @Override
    public void cachePermissions(UUID tenantId, UUID userId, Set<String> permissions, Duration ttl) {
        try {
            validateTtl(ttl);
            String key = buildPermsKey(tenantId, userId);
            String json = objectMapper.writeValueAsString(permissions != null ? permissions : Set.of());
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("[Redis写入权限缓存失败] key={}, error={}", buildPermsKey(tenantId, userId), e.getMessage());
            throw new ServiceUnavailableException("权限服务暂时不可用，请稍后重试", e);
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
            log.error("[Redis清除权限菜单缓存失败] tenantId={}, userId={}, error={}", tenantId, userId, e.getMessage());
            throw new ServiceUnavailableException("权限服务暂时不可用，请稍后重试", e);
        }
    }

    /**
     * 单独清除用户菜单快照，避免菜单配置变更时误删仍然有效的权限快照。
     * 入参为租户与用户标识，无返回值；Redis 删除失败直接抛出 503，调用方不得使用旧菜单继续放行。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     */
    @Override
    public void evictUserMenuCache(UUID tenantId, UUID userId) {
        String key = buildMenusKey(tenantId, userId);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("[Redis清除用户菜单缓存失败] key={}, error={}", key, e.getMessage());
            throw new ServiceUnavailableException("权限服务暂时不可用，请稍后重试", e);
        }
    }

    /**
     * 校验缓存 TTL，防止权限快照成为无过期时间的长期授权状态。
     *
     * @param ttl 待写入的存活时长
     * @throws ServiceUnavailableException TTL 缺失或不为正数
     */
    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new ServiceUnavailableException("权限缓存 TTL 无效，请稍后重试");
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
