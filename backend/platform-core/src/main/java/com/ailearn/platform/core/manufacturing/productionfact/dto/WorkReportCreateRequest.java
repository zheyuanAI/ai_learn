package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 创建不可变报工事实请求。 */
public record WorkReportCreateRequest(String reportNo, UUID operationExecutionId, UUID workOrderId,
                                      UUID operationId, OffsetDateTime reportTime,
                                      BigDecimal qualifiedQty, BigDecimal defectQty, String remark) {
}
