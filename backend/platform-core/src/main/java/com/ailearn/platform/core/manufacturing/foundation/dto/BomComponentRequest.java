package com.ailearn.platform.core.manufacturing.foundation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** BOM 创建请求明细；HTTP 层可将数量字符串转换为 BigDecimal 后传入。 */
public record BomComponentRequest(UUID componentProductId, BigDecimal componentQty,
                                  String uom, BigDecimal scrapRate) {
}
