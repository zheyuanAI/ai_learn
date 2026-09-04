package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** 领料/退料请求明细；租户和审计字段不由客户端提供。 */
public record MaterialItemRequest(UUID productId, UUID warehouseId, UUID locationId,
                                  BigDecimal quantity) {
}
