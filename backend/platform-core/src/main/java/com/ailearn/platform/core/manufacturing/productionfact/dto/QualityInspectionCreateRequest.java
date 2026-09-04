package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** 创建 Draft 质检请求。 */
public record QualityInspectionCreateRequest(String inspectionNo, UUID workReportId,
                                             String inspectionType, BigDecimal sampleQty) {
}
