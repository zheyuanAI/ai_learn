package com.ailearn.platform.core.traceability.ports;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** GIS 点位引用的源实体最小投影，不包含源领域写模型。 */
public record ReferencedEntity(UUID tenantId, String entityType, UUID entityId, String displayName,
                               String displayStatus, String detailPage, Instant sourceUpdatedAt,
                               boolean visible) {

    public ReferencedEntity {
        Objects.requireNonNull(tenantId, "实体租户不能为空");
        Objects.requireNonNull(entityType, "实体类型不能为空");
        Objects.requireNonNull(entityId, "实体标识不能为空");
        displayName = displayName == null ? "" : displayName;
        displayStatus = displayStatus == null ? "Normal" : displayStatus;
    }
}
