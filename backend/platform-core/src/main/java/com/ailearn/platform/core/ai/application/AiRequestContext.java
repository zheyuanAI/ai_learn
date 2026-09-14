package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/** AI 会话、工具和审计共用的可信请求上下文。 */
public record AiRequestContext(UUID tenantId, UUID userId, String sessionTokenId,
                               Set<String> permissions, ZoneId tenantZone,
                               String permissionFingerprint, String requestId) {
    public AiRequestContext {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("AI 请求必须包含可信租户和用户");
        }
        permissions = permissions == null ? Set.of() : Collections.unmodifiableSet(Set.copyOf(permissions));
        tenantZone = tenantZone == null ? ZoneId.of("UTC") : tenantZone;
        sessionTokenId = sessionTokenId == null ? "" : sessionTokenId;
        requestId = requestId == null || requestId.isBlank() ? "unknown-request" : requestId;
    }

    /** 判断当前可信权限快照是否包含指定权限。 */
    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    /** 转换为阶段 7 Facts 查询上下文，复用既有租户和节点权限裁剪。 */
    public FactsQueryContext toFactsContext() {
        return new FactsQueryContext(tenantId, permissionFingerprint, permissions, tenantZone, requestId);
    }
}
