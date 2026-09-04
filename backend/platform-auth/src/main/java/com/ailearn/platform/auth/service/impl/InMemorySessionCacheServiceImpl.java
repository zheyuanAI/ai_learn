package com.ailearn.platform.auth.service.impl;

import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存级会话与权限缓存实现类（仅供单元测试与离线模拟使用，严禁作为生产运行路径兜底）。
 */
@Component("inMemorySessionCacheServiceImpl")
@ConditionalOnProperty(prefix = "auth.session-cache", name = "type", havingValue = "memory")
public class InMemorySessionCacheServiceImpl implements SessionCacheService {

    private final Map<String, CacheEntry<String>> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Set<String>>> permsStore = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<MenuNodeVo>>> menusStore = new ConcurrentHashMap<>();

    private static class CacheEntry<T> {
        final T value;
        final Instant expireAt;

        CacheEntry(T value, Duration ttl) {
            this.value = value;
            this.expireAt = ttl != null ? Instant.now().plus(ttl) : Instant.MAX;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expireAt);
        }
    }

    private String buildKey(UUID tenantId, UUID userId) {
        return tenantId + ":" + userId;
    }

    @Override
    public boolean isRedisAvailable() {
        // 测试替身以进程内存储提供完整会话语义；对上层而言缓存中心是可用的，不能因未连接 Redis 误判为 503。
        return true;
    }

    @Override
    public void saveActiveSession(UUID tenantId, UUID userId, String jti, Duration ttl) {
        validateTtl(ttl);
        sessionStore.put(buildKey(tenantId, userId), new CacheEntry<>(jti, ttl));
    }

    @Override
    public String getActiveSessionJti(UUID tenantId, UUID userId) {
        CacheEntry<String> entry = sessionStore.get(buildKey(tenantId, userId));
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        return null;
    }

    /**
     * 读取内存测试会话的剩余 TTL，保持与 Redis 实现相同的刷新契约。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 剩余 TTL；会话不存在或已过期时返回 null
     */
    @Override
    public Duration getActiveSessionTtl(UUID tenantId, UUID userId) {
        CacheEntry<String> entry = sessionStore.get(buildKey(tenantId, userId));
        if (entry == null || entry.isExpired() || entry.expireAt.equals(Instant.MAX)) {
            // 无过期时间的测试会话没有可复用的有限 TTL，避免用人为的超长时长制造授权快照。
            return null;
        }
        Duration remaining = Duration.between(Instant.now(), entry.expireAt);
        return remaining.isZero() || remaining.isNegative() ? null : remaining;
    }

    @Override
    public void removeActiveSession(UUID tenantId, UUID userId) {
        sessionStore.remove(buildKey(tenantId, userId));
    }

    @Override
    public Set<String> getCachedPermissions(UUID tenantId, UUID userId) {
        CacheEntry<Set<String>> entry = permsStore.get(buildKey(tenantId, userId));
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        return null;
    }

    @Override
    public void cachePermissions(UUID tenantId, UUID userId, Set<String> permissions, Duration ttl) {
        validateTtl(ttl);
        Set<String> normalizedPermissions = permissions == null
                ? Set.of()
                : Set.copyOf(permissions);
        permsStore.put(buildKey(tenantId, userId), new CacheEntry<>(normalizedPermissions, ttl));
    }

    @Override
    public List<MenuNodeVo> getCachedMenus(UUID tenantId, UUID userId) {
        CacheEntry<List<MenuNodeVo>> entry = menusStore.get(buildKey(tenantId, userId));
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        return null;
    }

    @Override
    public void cacheMenus(UUID tenantId, UUID userId, List<MenuNodeVo> menus, Duration ttl) {
        menusStore.put(buildKey(tenantId, userId), new CacheEntry<>(menus, ttl));
    }

    @Override
    public void evictUserAuthCache(UUID tenantId, UUID userId) {
        String key = buildKey(tenantId, userId);
        permsStore.remove(key);
        menusStore.remove(key);
    }

    /**
     * 单独清除用户菜单快照，保持内存替身与 Redis 实现的失效边界一致。
     * 入参为租户与用户标识，无返回值；流程为按组合键删除菜单缓存，不触碰权限和会话缓存。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     */
    @Override
    public void evictUserMenuCache(UUID tenantId, UUID userId) {
        menusStore.remove(buildKey(tenantId, userId));
    }

    /**
     * 校验会话或权限快照 TTL，拒绝无过期时间的长期授权状态。
     *
     * @param ttl 待写入的 TTL
     */
    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new ServiceUnavailableException("权限缓存 TTL 无效，请稍后重试");
        }
    }

    @Override
    public void clearAll() {
        sessionStore.clear();
        permsStore.clear();
        menusStore.clear();
    }
}
