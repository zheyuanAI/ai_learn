package com.ailearn.platform.core.manufacturing.productionfact.application;

import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactSummary;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import com.ailearn.platform.core.manufacturing.productionfact.dto.FinishedGoodsReceiptCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialIssueCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialReturnCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionSubmitRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.WorkReportCreateRequest;
import java.util.List;
import java.util.UUID;

/** S5 Task16 生产事实应用端口。 */
public interface ProductionFactApplicationService {

    /** 创建生产领料 Draft。 */
    MaterialIssue createMaterialIssue(MaterialIssueCreateRequest request, String idempotencyKey);

    /** 确认生产领料并通过库存端口扣减。 */
    ProductionFactSummary confirmMaterialIssue(UUID id, String idempotencyKey);

    /** 创建生产退料 Draft。 */
    MaterialReturn createMaterialReturn(MaterialReturnCreateRequest request, String idempotencyKey);

    /** 确认生产退料并通过库存端口增加退回库位库存。 */
    ProductionFactSummary confirmMaterialReturn(UUID id, String idempotencyKey);

    /** 创建不可变报工事实。 */
    WorkReport createWorkReport(WorkReportCreateRequest request, String idempotencyKey);

    /** 查询当前租户工单下的报工事实。 */
    List<WorkReport> findWorkReports(UUID workOrderId);

    /** 创建 Draft 质检。 */
    QualityInspection createQualityInspection(QualityInspectionCreateRequest request,
                                               String idempotencyKey);

    /** 提交质检并进入 Passed 或 Failed。 */
    QualityInspection submitQualityInspection(UUID id, QualityInspectionSubmitRequest request,
                                               String idempotencyKey);

    /** 查询当前租户工单下的质检事实。 */
    List<QualityInspection> findQualityInspections(UUID workOrderId);

    /** 创建 Draft 成品入库。 */
    FinishedGoodsReceipt createFinishedGoodsReceipt(FinishedGoodsReceiptCreateRequest request,
                                                    String idempotencyKey);

    /** 确认成品入库并通过库存端口增加成品库存。 */
    ProductionFactSummary confirmFinishedGoodsReceipt(UUID id, String idempotencyKey);

    /** 查询当前租户工单下的成品入库事实。 */
    List<FinishedGoodsReceipt> findFinishedGoodsReceipts(UUID workOrderId);
}
