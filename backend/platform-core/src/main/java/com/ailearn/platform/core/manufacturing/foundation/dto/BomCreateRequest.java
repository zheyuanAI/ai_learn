package com.ailearn.platform.core.manufacturing.foundation.dto;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomStatus;
import java.util.List;
import java.util.UUID;

/** BOM 创建命令；不包含 tenantId，租户始终从可信上下文取得。 */
public record BomCreateRequest(String bomCode, UUID productId, String version, BomStatus status,
                               List<BomComponentRequest> components) {
}
