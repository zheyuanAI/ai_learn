package com.ailearn.platform.core.manufacturing.foundation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Routing 创建请求工序。 */
public record RoutingOperationRequest(int operationNo, String operationName, UUID workCenterId,
                                      BigDecimal standardTimeMinutes) {
}
