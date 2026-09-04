package com.ailearn.platform.core.manufacturing.foundation.dto;

import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingStatus;
import java.util.List;
import java.util.UUID;

/** Routing 创建命令；不包含 tenantId，租户始终从可信上下文取得。 */
public record RoutingCreateRequest(String routingCode, UUID productId, String version,
                                   RoutingStatus status, List<RoutingOperationRequest> operations) {
}
