package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycleRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspectionStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.ReferencedEntity;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 制造执行 Facts 生产适配器。
 * <p>
 * S7 通过 foundation、生命周期、工序执行和生产事实端口读取制造事实；不访问制造表，也不把生命周期
 * 汇总快照误当作报工、质检或库存事实。当前仓库没有可确认的 production_area 主数据来源，因此该引用
 * 查询保持为空，避免伪造生产区域实体。
 * </p>
 */
public class CoreManufacturingFactsAdapter implements ManufacturingFactsQuery {
    private final FoundationRepository foundation;
    private final WorkOrderLifecycleRepository lifecycleRepository;
    private final OperationExecutionRepository operationRepository;
    private final ProductionFactRepository productionRepository;

    public CoreManufacturingFactsAdapter(FoundationRepository foundation,
                                         WorkOrderLifecycleRepository lifecycleRepository,
                                         OperationExecutionRepository operationRepository,
                                         ProductionFactRepository productionRepository) {
        this.foundation = foundation;
        this.lifecycleRepository = lifecycleRepository;
        this.operationRepository = operationRepository;
        this.productionRepository = productionRepository;
    }

    @Override
    public FactsSummary manufacturing(FactsQueryRequest request) {
        try {
            List<WorkOrderFact> workOrders = foundation.findWorkOrders(request.context().tenantId());
            long orderCount = 0;
            long inProgressCount = 0;
            long completedCount = 0;
            long qualityBlockedCount = 0;
            BigDecimal planned = BigDecimal.ZERO;
            BigDecimal reported = BigDecimal.ZERO;
            BigDecimal qualified = BigDecimal.ZERO;
            BigDecimal defect = BigDecimal.ZERO;
            BigDecimal received = BigDecimal.ZERO;
            BigDecimal issued = BigDecimal.ZERO;
            BigDecimal returned = BigDecimal.ZERO;
            Instant updated = null;
            for (WorkOrderFact workOrder : workOrders) {
                if (!FactsAdapterSupport.inRange(workOrder.createdAt(), request)) {
                    continue;
                }
                orderCount++;
                planned = FactsAdapterSupport.add(planned, workOrder.plannedQty());
                WorkOrderLifecycle lifecycle = lifecycleRepository.find(request.context().tenantId(), workOrder.id())
                        .orElse(null);
                WorkOrderStatus status = lifecycle == null ? workOrder.status() : lifecycle.status();
                if (status == WorkOrderStatus.InProgress) {
                    inProgressCount++;
                } else if (status == WorkOrderStatus.Completed) {
                    completedCount++;
                }
                List<WorkReport> reports = productionRepository.findReports(request.context().tenantId(), workOrder.id());
                List<QualityInspection> inspections = productionRepository.findInspections(
                        request.context().tenantId(), workOrder.id());
                for (WorkReport report : reports) {
                    if (!FactsAdapterSupport.inRange(report.reportTime(), request)) {
                        continue;
                    }
                    reported = FactsAdapterSupport.add(reported, report.reportQty());
                    qualified = FactsAdapterSupport.add(qualified, report.qualifiedQty());
                    defect = FactsAdapterSupport.add(defect, report.defectQty());
                    updated = FactsAdapterSupport.later(updated,
                            FactsAdapterSupport.instant(report.reportTime()));
                }
                if (inspections.stream().anyMatch(value -> FactsAdapterSupport.inRange(value.updatedAt() == null
                        ? value.createdAt() : value.updatedAt(), request)
                        && (value.status() == QualityInspectionStatus.Draft
                        || value.status() == QualityInspectionStatus.Submitted
                        || value.status() == QualityInspectionStatus.Failed))) {
                    qualityBlockedCount++;
                }
                for (FinishedGoodsReceipt receipt : productionRepository.findReceipts(
                        request.context().tenantId(), workOrder.id())) {
                    if (receipt.status() == FinishedGoodsReceiptStatus.Confirmed
                            && FactsAdapterSupport.inRange(receipt.confirmedAt(), request)) {
                        received = FactsAdapterSupport.add(received, receipt.receiptQty());
                        updated = FactsAdapterSupport.later(updated,
                                FactsAdapterSupport.instant(receipt.confirmedAt()));
                    }
                }
                for (MaterialIssue issue : productionRepository.findIssues(
                        request.context().tenantId(), workOrder.id())) {
                    if (issue.status().name().equals("Confirmed")
                            && FactsAdapterSupport.inRange(issue.confirmedAt() == null
                            ? issue.createdAt() : issue.confirmedAt(), request)) {
                        issued = addLines(issued, issue.lines().stream()
                                .map(line -> line.issueQty()).toList());
                        updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(
                                issue.confirmedAt() == null ? issue.createdAt() : issue.confirmedAt()));
                    }
                }
                for (MaterialReturn materialReturn : productionRepository.findReturns(
                        request.context().tenantId(), workOrder.id())) {
                    if (materialReturn.status().name().equals("Confirmed")
                            && FactsAdapterSupport.inRange(materialReturn.confirmedAt() == null
                            ? materialReturn.createdAt() : materialReturn.confirmedAt(), request)) {
                        returned = addLines(returned, materialReturn.lines().stream()
                                .map(line -> line.returnQty()).toList());
                        updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(
                                materialReturn.confirmedAt() == null
                                        ? materialReturn.createdAt() : materialReturn.confirmedAt()));
                    }
                }
                updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(workOrder.createdAt()));
            }
            Map<String, BigDecimal> metrics = new java.util.LinkedHashMap<>();
            metrics.put("work_order_count", BigDecimal.valueOf(orderCount));
            metrics.put("planned_qty", planned);
            metrics.put("in_progress_count", BigDecimal.valueOf(inProgressCount));
            metrics.put("completed_count", BigDecimal.valueOf(completedCount));
            metrics.put("reported_qty", reported);
            metrics.put("qualified_qty", qualified);
            metrics.put("defect_qty", defect);
            metrics.put("received_qty", received);
            metrics.put("issued_qty", issued);
            metrics.put("returned_qty", returned);
            metrics.put("quality_blocked_count", BigDecimal.valueOf(qualityBlockedCount));
            return new FactsSummary(metrics, "manufacturing execution", updated);
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("manufacturing", exception);
        }
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        return manufacturing(request);
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        try {
            String type = query.entityType().trim().toLowerCase();
            UUID tenantId = query.context().tenantId();
            if (isWorkOrder(type)) {
                return workOrderFacts(tenantId, query.entityId());
            }
            if (isOperation(type)) {
                return operationFacts(tenantId, query.entityId());
            }
            if (isReport(type)) {
                return reportFacts(tenantId, query.entityId());
            }
            if (isInspection(type)) {
                return inspectionFacts(tenantId, query.entityId());
            }
            if (isReceipt(type)) {
                return receiptFacts(tenantId, query.entityId());
            }
            if (isIssue(type)) {
                return issueFacts(tenantId, query.entityId());
            }
            if (isReturn(type)) {
                return returnFacts(tenantId, query.entityId());
            }
            return TraceFacts.empty("manufacturing");
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("manufacturing", exception);
        }
    }

    /** 仓库中未确认独立 production_area 来源，不能用工单或 Routing 伪造该实体。 */
    @Override
    public Optional<ReferencedEntity> findProductionArea(com.ailearn.platform.core.traceability.ports.FactsQueryContext context,
                                                          UUID productionAreaId) {
        return Optional.empty();
    }

    private TraceFacts workOrderFacts(UUID tenantId, UUID workOrderId) {
        Optional<WorkOrderFact> found = foundation.findWorkOrder(tenantId, workOrderId);
        if (found.isEmpty()) {
            return TraceFacts.empty("manufacturing");
        }
        WorkOrderFact workOrder = found.get();
        WorkOrderLifecycle lifecycle = lifecycleRepository.find(tenantId, workOrderId).orElse(null);
        WorkOrderStatus status = lifecycle == null ? workOrder.status() : lifecycle.status();
        List<TraceNode> nodes = new ArrayList<>();
        List<TraceLink> links = new ArrayList<>();
        Instant updated = FactsAdapterSupport.instant(workOrder.createdAt());
        nodes.add(new TraceNode(tenantId, "work_order", workOrder.id(), workOrder.workOrderNo(),
                status.name(), "mes:workorder:view", updated, true));
        nodes.add(new TraceNode(tenantId, "bom", workOrder.bomId(), workOrder.bomVersion(),
                "ACTIVE", "mes:workorder:view", updated, true));
        nodes.add(new TraceNode(tenantId, "routing", workOrder.routingId(), workOrder.routingVersion(),
                "ACTIVE", "mes:workorder:view", updated, true));
        links.add(new TraceLink("work_order", workOrder.id(), "bom", workOrder.bomId(), "uses_bom"));
        links.add(new TraceLink("work_order", workOrder.id(), "routing", workOrder.routingId(), "uses_routing"));
        if (workOrder.sourceSalesOrderLineId() != null) {
            nodes.add(new TraceNode(tenantId, "sales_order_line", workOrder.sourceSalesOrderLineId(),
                    "sales-line-" + workOrder.sourceSalesOrderLineId(), "ACTIVE", "sales:order:view", updated, true));
            links.add(new TraceLink("sales_order_line", workOrder.sourceSalesOrderLineId(),
                    "work_order", workOrder.id(), "source_work_order"));
        }
        Set<UUID> completed = lifecycle == null ? Set.of() : lifecycle.progress().completedOperationIds();
        if (lifecycle != null) {
            for (UUID operationId : lifecycle.requiredOperationIds()) {
                boolean isCompleted = completed.contains(operationId);
                nodes.add(new TraceNode(tenantId, "operation", operationId, "operation-" + operationId,
                        isCompleted ? OperationExecutionStatus.Completed.name() : OperationExecutionStatus.NotStarted.name(),
                        "mes:execution:manage", updated, isCompleted));
                links.add(new TraceLink("work_order", workOrder.id(), "operation", operationId, "requires_operation"));
            }
        }
        for (WorkReport report : productionRepository.findReports(tenantId, workOrderId)) {
            nodes.add(reportNode(report));
            links.add(new TraceLink("work_order", workOrder.id(), "work_report", report.id(), "reports"));
            updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(report.reportTime()));
        }
        for (QualityInspection inspection : productionRepository.findInspections(tenantId, workOrderId)) {
            nodes.add(inspectionNode(inspection));
            links.add(new TraceLink("work_order", workOrder.id(), "mes_quality_inspection", inspection.id(), "quality_check"));
            updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(inspection.updatedAt()));
        }
        for (FinishedGoodsReceipt receipt : productionRepository.findReceipts(tenantId, workOrderId)) {
            nodes.add(receiptNode(receipt));
            links.add(new TraceLink("work_order", workOrder.id(), "finished_goods_receipt", receipt.id(), "finished_goods"));
            updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(receipt.updatedAt()));
        }
        for (MaterialIssue issue : productionRepository.findIssues(tenantId, workOrderId)) {
            nodes.add(materialNode(issue.tenantId(), "material_issue", issue.id(), issue.issueNo(),
                    issue.status().name(), issue.confirmedAt() == null ? issue.createdAt() : issue.confirmedAt()));
            links.add(new TraceLink("work_order", workOrder.id(), "material_issue", issue.id(), "material_issue"));
            updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(
                    issue.confirmedAt() == null ? issue.createdAt() : issue.confirmedAt()));
        }
        for (MaterialReturn materialReturn : productionRepository.findReturns(tenantId, workOrderId)) {
            nodes.add(materialNode(materialReturn.tenantId(), "material_return", materialReturn.id(),
                    materialReturn.returnNo(), materialReturn.status().name(),
                    materialReturn.confirmedAt() == null ? materialReturn.createdAt() : materialReturn.confirmedAt()));
            links.add(new TraceLink("work_order", workOrder.id(), "material_return", materialReturn.id(), "material_return"));
            updated = FactsAdapterSupport.later(updated, FactsAdapterSupport.instant(
                    materialReturn.confirmedAt() == null ? materialReturn.createdAt() : materialReturn.confirmedAt()));
        }
        return new TraceFacts(nodes, links, updated, "manufacturing execution");
    }

    private TraceFacts operationFacts(UUID tenantId, UUID executionId) {
        Optional<OperationExecution> found = operationRepository.find(tenantId, executionId);
        if (found.isEmpty()) {
            return TraceFacts.empty("manufacturing");
        }
        OperationExecution execution = found.get();
        List<TraceNode> nodes = new ArrayList<>();
        List<TraceLink> links = new ArrayList<>();
        Instant updated = FactsAdapterSupport.instant(execution.lastEventAt());
        nodes.add(new TraceNode(tenantId, "operation_execution", execution.id(), execution.operationId().toString(),
                execution.status().name(), "mes:execution:manage", updated, true));
        nodes.add(new TraceNode(tenantId, "work_order", execution.workOrderId(),
                "work-order-" + execution.workOrderId(), "ACTIVE", "mes:workorder:view", updated, true));
        nodes.add(new TraceNode(tenantId, "operation", execution.operationId(),
                "operation-" + execution.operationId(), execution.status().name(), "mes:execution:manage", updated,
                execution.status() == OperationExecutionStatus.Completed));
        links.add(new TraceLink("work_order", execution.workOrderId(), "operation_execution", execution.id(), "execution"));
        links.add(new TraceLink("operation_execution", execution.id(), "operation", execution.operationId(), "executes"));
        return new TraceFacts(nodes, links, updated, "manufacturing execution");
    }

    private TraceFacts reportFacts(UUID tenantId, UUID reportId) {
        Optional<WorkReport> found = productionRepository.findReport(tenantId, reportId);
        if (found.isEmpty()) {
            return TraceFacts.empty("manufacturing");
        }
        WorkReport report = found.get();
        TraceNode reportNode = reportNode(report);
        List<TraceNode> nodes = new ArrayList<>(List.of(reportNode));
        List<TraceLink> links = new ArrayList<>();
        operationRepository.find(tenantId, report.operationExecutionId()).ifPresent(execution -> {
            nodes.add(new TraceNode(tenantId, "operation_execution", execution.id(), execution.operationId().toString(),
                    execution.status().name(), "mes:execution:manage", FactsAdapterSupport.instant(execution.lastEventAt()), true));
            links.add(new TraceLink("operation_execution", execution.id(), "work_report", report.id(), "reports"));
        });
        nodes.add(new TraceNode(tenantId, "work_order", report.workOrderId(),
                "work-order-" + report.workOrderId(), "ACTIVE", "mes:workorder:view", FactsAdapterSupport.instant(report.reportTime()), true));
        links.add(new TraceLink("work_order", report.workOrderId(), "work_report", report.id(), "reports"));
        return new TraceFacts(nodes, links, FactsAdapterSupport.instant(report.reportTime()), "manufacturing execution");
    }

    private TraceFacts inspectionFacts(UUID tenantId, UUID inspectionId) {
        Optional<QualityInspection> found = productionRepository.findInspection(tenantId, inspectionId);
        if (found.isEmpty()) {
            return TraceFacts.empty("manufacturing");
        }
        QualityInspection inspection = found.get();
        List<TraceNode> nodes = new ArrayList<>(List.of(inspectionNode(inspection),
                new TraceNode(tenantId, "work_report", inspection.workReportId(),
                        "work-report-" + inspection.workReportId(), "ACTIVE", "mes:report:manage",
                        FactsAdapterSupport.instant(inspection.updatedAt()), true)));
        List<TraceLink> links = new ArrayList<>(List.of(
                new TraceLink("work_report", inspection.workReportId(), "mes_quality_inspection", inspection.id(), "quality_check")));
        nodes.add(new TraceNode(tenantId, "work_order", inspection.workOrderId(),
                "work-order-" + inspection.workOrderId(), "ACTIVE", "mes:workorder:view",
                FactsAdapterSupport.instant(inspection.updatedAt()), true));
        links.add(new TraceLink("work_order", inspection.workOrderId(), "mes_quality_inspection", inspection.id(), "quality_check"));
        return new TraceFacts(nodes, links, FactsAdapterSupport.instant(inspection.updatedAt()), "manufacturing execution");
    }

    private TraceFacts receiptFacts(UUID tenantId, UUID receiptId) {
        Optional<FinishedGoodsReceipt> found = productionRepository.findReceipt(tenantId, receiptId);
        if (found.isEmpty()) {
            return TraceFacts.empty("manufacturing");
        }
        FinishedGoodsReceipt receipt = found.get();
        TraceNode receiptNode = receiptNode(receipt);
        TraceNode workOrderNode = new TraceNode(tenantId, "work_order", receipt.workOrderId(),
                "work-order-" + receipt.workOrderId(), "ACTIVE", "mes:workorder:view",
                FactsAdapterSupport.instant(receipt.updatedAt()), true);
        return new TraceFacts(List.of(receiptNode, workOrderNode),
                List.of(new TraceLink("work_order", receipt.workOrderId(), "finished_goods_receipt", receipt.id(), "finished_goods")),
                FactsAdapterSupport.instant(receipt.updatedAt()), "manufacturing execution");
    }

    private TraceFacts issueFacts(UUID tenantId, UUID issueId) {
        Optional<MaterialIssue> found = productionRepository.findIssue(tenantId, issueId);
        return found.map(value -> materialFacts(tenantId, "material_issue", value.id(), value.workOrderId(), value.status().name(),
                value.createdAt(), "material issue")).orElseGet(() -> TraceFacts.empty("manufacturing"));
    }

    private TraceFacts returnFacts(UUID tenantId, UUID returnId) {
        Optional<MaterialReturn> found = productionRepository.findReturn(tenantId, returnId);
        return found.map(value -> materialFacts(tenantId, "material_return", value.id(), value.workOrderId(), value.status().name(),
                value.createdAt(), "material return")).orElseGet(() -> TraceFacts.empty("manufacturing"));
    }

    private TraceFacts materialFacts(UUID tenantId, String type, UUID id, UUID workOrderId, String status,
                                     java.time.OffsetDateTime updatedAt, String summary) {
        Instant updated = FactsAdapterSupport.instant(updatedAt);
        List<TraceNode> nodes = new ArrayList<>();
        List<TraceLink> links = new ArrayList<>();
        nodes.add(materialNode(tenantId, type, id, type + "-" + id, status, updatedAt));
        foundation.findWorkOrder(tenantId, workOrderId).ifPresent(workOrder -> {
            nodes.add(new TraceNode(tenantId, "work_order", workOrder.id(), workOrder.workOrderNo(),
                    workOrder.status().name(), "mes:workorder:view", FactsAdapterSupport.instant(workOrder.createdAt()), true));
            links.add(new TraceLink("work_order", workOrder.id(), type, id, "material_fact"));
        });
        return new TraceFacts(nodes, links, updated, summary);
    }

    /** 构造领退料事实节点，确认状态仍由具体生产单据决定，不虚构库存余额。 */
    private static TraceNode materialNode(UUID tenantId, String type, UUID id, String label,
                                          String status, java.time.OffsetDateTime updatedAt) {
        return new TraceNode(tenantId, type, id, label, status,
                "mes:material:requisition", FactsAdapterSupport.instant(updatedAt), true);
    }

    private static TraceNode reportNode(WorkReport report) {
        return new TraceNode(report.tenantId(), "work_report", report.id(), report.reportNo(),
                "RECORDED", "mes:report:manage", FactsAdapterSupport.instant(report.reportTime()), true);
    }

    private static TraceNode inspectionNode(QualityInspection inspection) {
        return new TraceNode(inspection.tenantId(), "mes_quality_inspection", inspection.id(), inspection.inspectionNo(),
                inspection.status().name(), "mes:quality:inspect", FactsAdapterSupport.instant(inspection.updatedAt()), true);
    }

    private static TraceNode receiptNode(FinishedGoodsReceipt receipt) {
        return new TraceNode(receipt.tenantId(), "finished_goods_receipt", receipt.id(), receipt.receiptNo(),
                receipt.status().name(), "mes:finished:receipt", FactsAdapterSupport.instant(receipt.updatedAt()), true);
    }

    private static BigDecimal addLines(BigDecimal current, List<BigDecimal> quantities) {
        BigDecimal result = current;
        for (BigDecimal quantity : quantities) {
            result = FactsAdapterSupport.add(result, quantity);
        }
        return result;
    }

    private static boolean isWorkOrder(String type) {
        return type.equals("work_order") || type.equals("mes_work_order");
    }

    private static boolean isOperation(String type) {
        return type.equals("operation_execution") || type.equals("operation");
    }

    private static boolean isReport(String type) {
        return type.equals("work_report") || type.equals("mes_work_report");
    }

    private static boolean isInspection(String type) {
        return type.equals("mes_quality_inspection") || type.equals("quality_inspection");
    }

    private static boolean isReceipt(String type) {
        return type.equals("finished_goods_receipt") || type.equals("mes_finished_goods_receipt");
    }

    private static boolean isIssue(String type) {
        return type.equals("material_issue") || type.equals("mes_material_issue");
    }

    private static boolean isReturn(String type) {
        return type.equals("material_return") || type.equals("mes_material_return");
    }
}
