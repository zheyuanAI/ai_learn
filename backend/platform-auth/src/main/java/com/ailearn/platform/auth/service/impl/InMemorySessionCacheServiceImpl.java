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

        /**
         * 创建带过期时间的测试缓存条目；缺少 TTL 时使用永久时间点，以保持内存替身对菜单缓存的原有兼容语义。
         */
        CacheEntry(T value, Duration ttl) {
            this.value = value;
            this.expireAt = ttl != null ? Instant.now().plus(ttl) : Instant.MAX;
        }

        /**
         * 判断条目是否已过期；调用方据此将过期项视为缓存未命中。
         */
        boolean isExpired() {
            return Instant.now().isAfter(expireAt);
        }
    }

    /**
     * 构造测试缓存的租户-用户组合键，保持内存实现与 Redis 实现相同的租户隔离边界。
     */
    private String buildKey(UUID tenantId, UUID userId) {
        return tenantId + ":" + userId;
    }

    /**
     * 在测试替身中报告缓存中心可用；这不表示生产环境可以省略 Redis 依赖检查。
     */
    @Override
    public boolean isRedisAvailable() {
        // 测试替身以进程内存储提供完整会话语义；对上层而言缓存中心是可用的，不能因未连接 Redis 误判为 503。
        return true;
    }

    /**
     * 保存测试用活跃会话并校验有限 TTL，用于模拟单账号单会话校验。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param jti 本次登录的 JWT 唯一标识
     * @param ttl 会话有效期
     */
    @Override
    public void saveActiveSession(UUID tenantId, UUID userId, String jti, Duration ttl) {
        validateTtl(ttl);
        sessionStore.put(buildKey(tenantId, userId), new CacheEntry<>(jti, ttl));
    }

    /**
     * 读取测试用活跃会话；过期或不存在都按未命中处理。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 当前 JTI；未命中时返回 null
     */
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

    /**
     * 删除测试用活跃会话，模拟登出或会话失效后的即时撤销。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     */
    @Override
    public void removeActiveSession(UUID tenantId, UUID userId) {
        sessionStore.remove(buildKey(tenantId, userId));
    }

    /**
     * 读取测试用权限快照；过期和缺失均返回 null，由上层回源重建。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 权限快照；未命中时返回 null
     */
    @Override
    public Set<String> getCachedPermissions(UUID tenantId, UUID userId) {
        CacheEntry<Set<String>> entry = permsStore.get(buildKey(tenantId, userId));
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        return null;
    }

    /**
     * 写入测试用权限快照并复制集合，避免调用方后续修改原集合而污染缓存内容。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param permissions 待缓存的权限编码
     * @param ttl 权限快照有效期
     */
    @Override
    public void cachePermissions(UUID tenantId, UUID userId, Set<String> permissions, Duration ttl) {
        validateTtl(ttl);
        Set<String> normalizedPermissions = permissions == null
                ? Set.of()
                : Set.copyOf(permissions);
        permsStore.put(buildKey(tenantId, userId), new CacheEntry<>(normalizedPermissions, ttl));
    }

    /**
     * 读取测试用菜单快照；过期和缺失均返回 null，不把缓存内容当作菜单权威来源。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 菜单快照；未命中时返回 null
     */
    @Override
    public List<MenuNodeVo> getCachedMenus(UUID tenantId, UUID userId) {
        CacheEntry<List<MenuNodeVo>> entry = menusStore.get(buildKey(tenantId, userId));
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        return null;
    }

    /**
     * 写入测试用菜单快照，复现 Redis 菜单缓存的可选 TTL 语义。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param menus 待缓存的菜单树
     * @param ttl 菜单快照有效期；为 null 时由测试替身保留为不自动过期
     */
    @Override
    public void cacheMenus(UUID tenantId, UUID userId, List<MenuNodeVo> menus, Duration ttl) {
        menusStore.put(buildKey(tenantId, userId), new CacheEntry<>(menus, ttl));
    }

    /**
     * 同时清除测试用权限与菜单快照，保持与 Redis 实现一致的用户授权缓存失效边界。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     */
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

    /**
     * 清空测试替身维护的会话、权限和菜单缓存；不涉及数据库或其他业务状态。
     */
    @Override
    public void clearAll() {
        sessionStore.clear();
        permsStore.clear();
        menusStore.clear();
    }
}
