package com.ailearn.platform.core.traceability.ports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 追溯节点事实；requiredPermission 用于应用层二次裁剪。 */
public record TraceNode(UUID tenantId, String entityType, UUID entityId, String label, String status,
                        String requiredPermission, Instant sourceUpdatedAt, boolean complete) {

    public TraceNode {
        Objects.requireNonNull(tenantId, "节点租户不能为空");
        Objects.requireNonNull(entityType, "节点类型不能为空");
        Objects.requireNonNull(entityId, "节点标识不能为空");
        label = label == null ? "" : label;
        status = status == null ? "" : status;
        requiredPermission = requiredPermission == null ? "" : requiredPermission;
    }
}
