package com.ailearn.platform.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * 下游服务权限上下文读取端口。
 * <p>
 * 该端口只读取网关已经确认的用户身份对应的集中式权限快照，禁止从客户端 Header 或业务数据库自行回源。
 * </p>
 */
@FunctionalInterface
public interface PermissionContextReader {

    /**
     * 读取当前租户用户的权限快照。
     *
     * @param tenantId 受信任的租户 ID
     * @param userId 受信任的用户 ID
     * @return 权限码集合；空集合表示已认证但没有权限
     * @throws com.ailearn.platform.shared.exception.ServiceUnavailableException 权限缓存缺失或基础设施异常
     */
    Set<String> readPermissions(UUID tenantId, UUID userId);
}
