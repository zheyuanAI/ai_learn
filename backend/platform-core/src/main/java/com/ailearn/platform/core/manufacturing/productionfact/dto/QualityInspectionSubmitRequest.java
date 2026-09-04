package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.math.BigDecimal;

/** 提交质检结果请求。 */
public record QualityInspectionSubmitRequest(BigDecimal qualifiedQty, BigDecimal defectQty,
                                             String result) {
}
