package com.ailearn.platform.auth.service;

import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 用户会话状态与权限/菜单缓存服务接口。
 * <p>
 * 管理 Redis 中的：
 * 1. 单账号单有效会话控制：auth:session:{tenantId}:{userId} -> jti
 * 2. 用户功能权限点缓存：auth:perms:{tenantId}:{userId} -> Set&lt;String&gt;
 * 3. 用户动态菜单树缓存：auth:menus:{tenantId}:{userId} -> List&lt;MenuNodeVo&gt;
 * </p>
 */
public interface SessionCacheService {

    /**
     * 探测 Redis 缓存服务是否可用。
     *
     * @return 若 Redis 可连通且响应 PONG 返回 true，否则返回 false
     */
    boolean isRedisAvailable();

    /**
     * 写入用户当前活跃会话 JTI（实现后登顶前）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param jti      会话 JTI
     * @param ttl      存活时长
     */
    void saveActiveSession(UUID tenantId, UUID userId, String jti, Duration ttl);

    /**
     * 获取用户当前活跃会话 JTI。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 当前活跃 JTI，若无则返回 null
     */
    String getActiveSessionJti(UUID tenantId, UUID userId);

    /**
     * 删除用户的活跃会话记录（主动注销）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     */
    void removeActiveSession(UUID tenantId, UUID userId);

    /**
     * 读取用户功能权限点缓存。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 权限点集合，若缓存未命中返回 null
     */
    Set<String> getCachedPermissions(UUID tenantId, UUID userId);

    /**
     * 写入用户功能权限点缓存。
     *
     * @param tenantId    租户 ID
     * @param userId      用户 ID
     * @param permissions 权限点集合
     * @param ttl         存活时长
     */
    void cachePermissions(UUID tenantId, UUID userId, Set<String> permissions, Duration ttl);

    /**
     * 读取用户动态菜单树缓存。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 菜单节点树列表，若未命中返回 null
     */
    List<MenuNodeVo> getCachedMenus(UUID tenantId, UUID userId);

    /**
     * 写入用户动态菜单树缓存。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param menus    菜单树列表
     * @param ttl      存活时长
     */
    void cacheMenus(UUID tenantId, UUID userId, List<MenuNodeVo> menus, Duration ttl);

    /**
     * 主动清除指定用户的权限与菜单缓存（在用户角色变更、权限变更或注销时调用）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     */
    void evictUserAuthCache(UUID tenantId, UUID userId);

    /**
     * 清理所有缓存（开发与测试环境辅助）。
     */
    void clearAll();
}
