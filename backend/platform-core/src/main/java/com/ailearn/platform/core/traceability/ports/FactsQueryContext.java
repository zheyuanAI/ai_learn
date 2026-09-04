package com.ailearn.platform.core.traceability.ports;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 跨域事实查询的可信上下文。
 * <p>
 * tenantId、权限集合和权限指纹必须由认证边界组装，GIS、看板和追溯不得信任客户端传入的租户条件。
 * </p>
 */
public record FactsQueryContext(UUID tenantId, String permissionFingerprint, Set<String> permissions,
                                ZoneId tenantZone, String requestId) {

    public FactsQueryContext {
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        if (permissionFingerprint == null || permissionFingerprint.isBlank()) {
            throw new IllegalArgumentException("permissionFingerprint 不能为空");
        }
        permissions = permissions == null ? Set.of() : Collections.unmodifiableSet(Set.copyOf(permissions));
        tenantZone = tenantZone == null ? ZoneId.of("UTC") : tenantZone;
        requestId = requestId == null || requestId.isBlank() ? "unknown-request" : requestId;
    }

    /** 判断当前可信权限快照是否包含指定权限。 */
    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }
}
