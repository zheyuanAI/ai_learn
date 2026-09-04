package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 报工质量检验聚合，对应 V5 mes_quality_inspection。 */
public record QualityInspection(UUID id, UUID tenantId, String inspectionNo, UUID workReportId,
                                UUID workOrderId, UUID operationId, String inspectionType,
                                BigDecimal sampleQty, BigDecimal qualifiedQty, BigDecimal defectQty,
                                String result, QualityInspectionStatus status, UUID submittedBy,
                                OffsetDateTime submittedAt, UUID createdBy, OffsetDateTime createdAt,
                                UUID updatedBy, OffsetDateTime updatedAt) {

    public QualityInspection {
        if (tenantId == null || id == null || inspectionNo == null || inspectionNo.isBlank()
                || inspectionNo.length() > 64 || workReportId == null || workOrderId == null
                || operationId == null || inspectionType == null || inspectionType.isBlank()
                || inspectionType.length() > 64 || sampleQty == null || sampleQty.signum() <= 0
                || sampleQty.scale() > 6 || qualifiedQty == null || defectQty == null
                || qualifiedQty.signum() < 0 || defectQty.signum() < 0 || status == null
                || createdBy == null || createdAt == null) {
            throw new IllegalArgumentException("质检字段或数量不合法");
        }
        if (qualifiedQty.add(defectQty).compareTo(sampleQty) > 0) {
            throw new IllegalArgumentException("质检合格数与不良数不能超过抽检数");
        }
        if (status == QualityInspectionStatus.Draft
                && (result != null || submittedBy != null || submittedAt != null)) {
            throw new IllegalArgumentException("Draft 质检不能携带提交事实");
        }
        if (status != QualityInspectionStatus.Draft
                && (result == null || submittedBy == null || submittedAt == null)) {
            throw new IllegalArgumentException("已提交质检必须保留结果和审计事实");
        }
    }

    /** 创建 Draft 质检单。 */
    public static QualityInspection draft(UUID id, UUID tenantId, String inspectionNo,
                                          UUID workReportId, UUID workOrderId, UUID operationId,
                                          String inspectionType, BigDecimal sampleQty, UUID userId,
                                          OffsetDateTime now) {
        return new QualityInspection(id, tenantId, inspectionNo, workReportId, workOrderId, operationId,
                inspectionType, sampleQty, BigDecimal.ZERO, BigDecimal.ZERO, null,
                QualityInspectionStatus.Draft, null, null, userId, now, userId, now);
    }

    /** 按 Passed/Failed 结果提交质检。 */
    public QualityInspection submit(BigDecimal qualified, BigDecimal defect, String submittedResult,
                                     UUID userId, OffsetDateTime now) {
        if (submittedResult == null || submittedResult.isBlank()) {
            throw new IllegalArgumentException("质检结果不能为空");
        }
        QualityInspectionStatus next = switch (submittedResult.trim().toUpperCase()) {
            case "PASSED" -> {
                if (defect.signum() != 0) {
                    throw new IllegalArgumentException("Passed 质检的不良数必须为 0");
                }
                yield QualityInspectionStatus.Passed;
            }
            case "FAILED" -> {
                if (defect.signum() == 0) {
                    throw new IllegalArgumentException("Failed 质检必须记录不良数");
                }
                yield QualityInspectionStatus.Failed;
            }
            default -> throw new IllegalArgumentException("质检结果只能是 Passed 或 Failed");
        };
        return new QualityInspection(id, tenantId, inspectionNo, workReportId, workOrderId, operationId,
                inspectionType, sampleQty, qualified, defect, next.name(), next, userId, now,
                createdBy, createdAt, userId, now);
    }
}
