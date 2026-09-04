package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 工序报工事实，对应 V5 mes_work_report。 */
public record WorkReport(UUID id, UUID tenantId, String reportNo, UUID operationExecutionId,
                         UUID workOrderId, UUID operationId, OffsetDateTime reportTime,
                         BigDecimal qualifiedQty, BigDecimal defectQty, BigDecimal reportQty,
                         String remark, UUID createdBy, OffsetDateTime createdAt) {

    public WorkReport {
        if (tenantId == null || id == null || reportNo == null || reportNo.isBlank()
                || reportNo.length() > 64 || operationExecutionId == null || workOrderId == null
                || operationId == null || reportTime == null || createdBy == null || createdAt == null
                || qualifiedQty == null || defectQty == null || qualifiedQty.signum() < 0
                || defectQty.signum() < 0 || qualifiedQty.scale() > 6 || defectQty.scale() > 6) {
            throw new IllegalArgumentException("报工字段或数量不合法");
        }
        BigDecimal calculated = qualifiedQty.add(defectQty);
        if (calculated.signum() <= 0 || reportQty == null || reportQty.compareTo(calculated) != 0) {
            throw new IllegalArgumentException("报工总数必须等于合格数加不良数");
        }
        if (remark != null && remark.length() > 512) {
            throw new IllegalArgumentException("报工备注长度不能超过 512");
        }
    }

    /** 创建不可变报工事实，派生并保存 report_qty。 */
    public static WorkReport create(UUID id, UUID tenantId, String reportNo, UUID operationExecutionId,
                                    UUID workOrderId, UUID operationId, OffsetDateTime reportTime,
                                    BigDecimal qualifiedQty, BigDecimal defectQty, String remark,
                                    UUID userId, OffsetDateTime now) {
        return new WorkReport(id, tenantId, reportNo, operationExecutionId, workOrderId, operationId,
                reportTime, qualifiedQty, defectQty, qualifiedQty.add(defectQty), remark, userId, now);
    }
}
