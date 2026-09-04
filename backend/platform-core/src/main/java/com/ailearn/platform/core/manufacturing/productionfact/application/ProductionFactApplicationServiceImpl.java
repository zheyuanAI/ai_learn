package com.ailearn.platform.core.manufacturing.productionfact.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryDecreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.BomFactsPort;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionStatus;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialDocumentStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssueLine;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturnLine;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactSummary;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspectionStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import com.ailearn.platform.core.manufacturing.productionfact.dto.FinishedGoodsReceiptCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialIssueCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialItemRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialReturnCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionSubmitRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.WorkReportCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.exception.ProductionFactErrorCode;
import com.ailearn.platform.core.manufacturing.productionfact.exception.ProductionFactException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task16 生产事实应用服务。
 * <p>
 * 入参：不含租户和操作人的业务请求；出参：生产事实或库存流水关联；流程：可信上下文与工单租户校验、
 * 状态/数量校验、幂等协调、库存命令端口调用、事实端口保存。服务不直接注入库存 Mapper。
 * </p>
 */
@Service
public class ProductionFactApplicationServiceImpl implements ProductionFactApplicationService {

    private final ProductionFactRepository repository;
    private final InventoryCommandService inventoryCommandService;
    private final WorkOrderExecutionService workOrderService;
    private final BomFactsPort bomFactsPort;
    private final OperationExecutionRepository operationExecutionRepository;
    private final InventoryLocationPort inventoryLocationPort;
    private final CoreIdempotencyExecutor idempotency;
    private final ObjectMapper objectMapper;

    /** 使用内存幂等器构造 focused tests 服务。 */
    public ProductionFactApplicationServiceImpl(ProductionFactRepository repository,
                                                InventoryCommandService inventoryCommandService,
                                                WorkOrderExecutionService workOrderService) {
        this(repository, inventoryCommandService, workOrderService,
                null, null, null,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /** 保留旧生产测试构造器；真实 Spring Bean 使用下方带完整边界端口的构造器。 */
    public ProductionFactApplicationServiceImpl(ProductionFactRepository repository,
                                                InventoryCommandService inventoryCommandService,
                                                WorkOrderExecutionService workOrderService,
                                                BomFactsPort bomFactsPort,
                                                IdempotencyStorage storage, ObjectMapper objectMapper) {
        this(repository, inventoryCommandService, workOrderService, bomFactsPort, null, null,
                storage, objectMapper);
    }

    /** 创建可替换幂等存储和序列化器的生产服务。 */
    @Autowired
    public ProductionFactApplicationServiceImpl(ProductionFactRepository repository,
                                                InventoryCommandService inventoryCommandService,
                                                WorkOrderExecutionService workOrderService,
                                                BomFactsPort bomFactsPort,
                                                OperationExecutionRepository operationExecutionRepository,
                                                InventoryLocationPort inventoryLocationPort,
                                                IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.repository = repository;
        this.inventoryCommandService = inventoryCommandService;
        this.workOrderService = workOrderService;
        this.bomFactsPort = bomFactsPort;
        this.operationExecutionRepository = operationExecutionRepository;
        this.inventoryLocationPort = inventoryLocationPort;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.idempotency = new CoreIdempotencyExecutor(storage, this.objectMapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:material:requisition')")
    public MaterialIssue createMaterialIssue(MaterialIssueCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        validateMaterialRequest(request == null ? null : request.issueNo(),
                request == null ? null : request.workOrderId(),
                request == null ? null : request.items());
        return idempotency.execute("mes:material-issue:create", actor.tenantId(), idempotencyKey,
                digest(request), MaterialIssue.class, () -> {
                    WorkOrderLifecycle lifecycle = requireExecutableWorkOrder(request.workOrderId());
                    List<MaterialIssueLine> lines = issueLines(request.items());
                    requireBomMaterials(lifecycle, lines.stream().map(MaterialIssueLine::productId).toList());
                    return repository.saveIssue(MaterialIssue.draft(UUID.randomUUID(), actor.tenantId(),
                            request.issueNo().trim(), request.workOrderId(), lines, actor.userId(), now()));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:material:confirm')")
    public ProductionFactSummary confirmMaterialIssue(UUID id, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        requireId(id, "materialIssueId");
        return idempotency.execute("mes:material-issue:confirm", actor.tenantId(), idempotencyKey,
                digest(List.of(id)), ProductionFactSummary.class, () -> {
                    MaterialIssue issue = requiredIssue(actor.tenantId(), id);
                    WorkOrderLifecycle lifecycle = requireExecutableWorkOrder(issue.workOrderId());
                    requireBomMaterials(lifecycle, issue.lines().stream().map(MaterialIssueLine::productId).toList());
                    if (issue.status() != MaterialDocumentStatus.Draft) {
                        throw error(ProductionFactErrorCode.MES_FACT_001, "领料单不是 Draft 状态");
                    }
                    List<UUID> transactionIds = new ArrayList<>();
                    for (MaterialIssueLine line : issue.lines()) {
                        InventoryMutationResult result = inventoryCommandService.decrease(
                                new InventoryDecreaseCommand(decreaseMetadata(actor, issue.id(), line.id(),
                                        idempotencyKey), new InventoryDimension(line.productId(), line.warehouseId(),
                                        line.locationId(), ""), line.issueQty()));
                        transactionIds.add(requiredTransaction(result, ProductionFactErrorCode.MES_MAT_001));
                    }
                    UUID operationId = operationId("issue", issue.id());
                    MaterialIssue confirmed = repository.updateIssue(actor.tenantId(), id,
                            current -> current.status() == MaterialDocumentStatus.Draft
                                    ? current.confirmed(transactionIds, operationId, actor.userId(),
                                    actor.sessionId(), now())
                                    : failStatus("领料单已被其他命令确认"));
                    if (confirmed == null) {
                        throw error(ProductionFactErrorCode.MES_TENANT_001, "领料单不存在或不属于当前租户");
                    }
                    return new ProductionFactSummary("MATERIAL_ISSUE", confirmed.id(), confirmed,
                            issue.lines().stream().map(MaterialIssueLine::issueQty)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add), transactionIds);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:material:requisition')")
    public MaterialReturn createMaterialReturn(MaterialReturnCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        validateMaterialRequest(request == null ? null : request.returnNo(),
                request == null ? null : request.workOrderId(),
                request == null ? null : request.items());
        return idempotency.execute("mes:material-return:create", actor.tenantId(), idempotencyKey,
                digest(request), MaterialReturn.class, () -> {
                    WorkOrderLifecycle lifecycle = requireExecutableWorkOrder(request.workOrderId());
                    List<MaterialReturnLine> lines = returnLines(request.items());
                    requireBomMaterials(lifecycle, lines.stream().map(MaterialReturnLine::productId).toList());
                    repository.lockWorkOrder(actor.tenantId(), request.workOrderId());
                    for (MaterialReturnLine line : lines) {
                        if (returnableQuantity(actor.tenantId(), request.workOrderId(), line.productId())
                                .compareTo(line.returnQty()) < 0) {
                            throw error(ProductionFactErrorCode.MES_MAT_002, "退料数量超过已领未退数量");
                        }
                    }
                    return repository.saveReturn(MaterialReturn.draft(UUID.randomUUID(), actor.tenantId(),
                            request.returnNo().trim(), request.workOrderId(), lines, actor.userId(), now()));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:material:confirm')")
    public ProductionFactSummary confirmMaterialReturn(UUID id, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        requireId(id, "materialReturnId");
        return idempotency.execute("mes:material-return:confirm", actor.tenantId(), idempotencyKey,
                digest(List.of(id)), ProductionFactSummary.class, () -> {
                    MaterialReturn value = requiredReturn(actor.tenantId(), id);
                    WorkOrderLifecycle lifecycle = requireExecutableWorkOrder(value.workOrderId());
                    requireBomMaterials(lifecycle, value.lines().stream().map(MaterialReturnLine::productId).toList());
                    repository.lockWorkOrder(actor.tenantId(), value.workOrderId());
                    if (value.status() != MaterialDocumentStatus.Draft) {
                        throw error(ProductionFactErrorCode.MES_FACT_001, "退料单不是 Draft 状态");
                    }
                    // 确认前再次按当前租户汇总已确认领退料，避免 Draft 创建后并发确认造成超退。
                    for (MaterialReturnLine line : value.lines()) {
                        if (returnableQuantity(actor.tenantId(), value.workOrderId(), line.productId())
                                .compareTo(line.returnQty()) < 0) {
                            throw error(ProductionFactErrorCode.MES_MAT_002, "确认时退料数量超过已领未退数量");
                        }
                    }
                    List<UUID> transactionIds = new ArrayList<>();
                    for (MaterialReturnLine line : value.lines()) {
                        InventoryMutationResult result = inventoryCommandService.increase(
                                new InventoryIncreaseCommand(increaseMetadata(actor, value.id(), line.id(),
                                        idempotencyKey, "MATERIAL_RETURN", "MATERIAL_RETURN"),
                                        new InventoryDimension(line.productId(), line.warehouseId(), line.locationId(), ""),
                                        line.returnQty()));
                        transactionIds.add(requiredTransaction(result, ProductionFactErrorCode.MES_MAT_002));
                    }
                    UUID operationId = operationId("return", value.id());
                    MaterialReturn confirmed = repository.updateReturn(actor.tenantId(), id,
                            current -> current.status() == MaterialDocumentStatus.Draft
                                    ? current.confirmed(transactionIds, operationId, actor.userId(),
                                    actor.sessionId(), now())
                                    : failStatus("退料单已被其他命令确认"));
                    if (confirmed == null) {
                        throw error(ProductionFactErrorCode.MES_TENANT_001, "退料单不存在或不属于当前租户");
                    }
                    return new ProductionFactSummary("MATERIAL_RETURN", confirmed.id(), confirmed,
                            value.lines().stream().map(MaterialReturnLine::returnQty)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add), transactionIds);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:report:manage')")
    public WorkReport createWorkReport(WorkReportCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        if (request == null) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "报工请求不能为空");
        }
        return idempotency.execute("mes:work-report:create", actor.tenantId(), idempotencyKey,
                 digest(request), WorkReport.class, () -> {
                     requireId(request.workOrderId(), "workOrderId");
                     requireId(request.operationExecutionId(), "operationExecutionId");
                     requireId(request.operationId(), "operationId");
                     // 先锁工单主表，再读取累计报工，保证并发请求不能同时通过数量上限检查。
                     repository.lockWorkOrder(actor.tenantId(), request.workOrderId());
                     WorkOrderLifecycle lifecycle = requireInProgressWorkOrder(request.workOrderId());
                     validateCompletedOperationExecution(actor.tenantId(), request.operationExecutionId(),
                             request.workOrderId(), request.operationId());
                     if (!lifecycle.requiredOperationIds().contains(request.operationId())) {
                        throw error(ProductionFactErrorCode.MES_FACT_002, "报工工序不属于工单冻结 Routing");
                    }
                    BigDecimal quantity = positive(request.qualifiedQty(), "qualifiedQty", false)
                            .add(positive(request.defectQty(), "defectQty", false));
                    if (quantity.signum() <= 0) {
                        throw error(ProductionFactErrorCode.MES_FACT_002, "报工数量必须大于 0");
                    }
                    BigDecimal existing = repository.findReports(actor.tenantId(), request.workOrderId()).stream()
                            .map(WorkReport::reportQty).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (existing.add(quantity).compareTo(lifecycle.workOrder().plannedQty()) > 0) {
                        throw error(ProductionFactErrorCode.MES_WO_003, "累计报工数量超出工单计划数量");
                    }
                    WorkReport saved = repository.saveReport(WorkReport.create(UUID.randomUUID(), actor.tenantId(),
                            requiredText(request.reportNo(), "reportNo"), request.operationExecutionId(),
                            request.workOrderId(), request.operationId(), requiredTime(request.reportTime()),
                            request.qualifiedQty(), request.defectQty(), request.remark(), actor.userId(), now()));
                    synchronizeProgress(actor.tenantId(), request.workOrderId(), idempotencyKey);
                    return saved;
                });
    }

    @Override
    @PreAuthorize("hasAuthority('mes:report:manage')")
    public List<WorkReport> findWorkReports(UUID workOrderId) {
        Actor actor = actor();
        requireId(workOrderId, "workOrderId");
        if (!visibleWorkOrder(actor.tenantId(), workOrderId)) {
            return List.of();
        }
        return repository.findReports(actor.tenantId(), workOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:quality:inspect')")
    public QualityInspection createQualityInspection(QualityInspectionCreateRequest request,
                                                     String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        if (request == null) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "质检请求不能为空");
        }
        return idempotency.execute("mes:quality-inspection:create", actor.tenantId(), idempotencyKey,
                digest(request), QualityInspection.class, () -> {
                    WorkReport report = repository.findReport(actor.tenantId(), request.workReportId())
                            .orElseThrow(() -> error(ProductionFactErrorCode.MES_TENANT_001,
                                    "报工不存在或不属于当前租户"));
                    requireInProgressWorkOrder(report.workOrderId());
                    positive(request.sampleQty(), "sampleQty", true);
                    if (request.sampleQty().compareTo(report.reportQty()) > 0) {
                        throw error(ProductionFactErrorCode.MES_FACT_002, "抽检数不能超过报工总数");
                    }
                    return repository.saveInspection(QualityInspection.draft(UUID.randomUUID(), actor.tenantId(),
                            requiredText(request.inspectionNo(), "inspectionNo"), report.id(), report.workOrderId(),
                            report.operationId(), requiredText(request.inspectionType(), "inspectionType"),
                            request.sampleQty(), actor.userId(), now()));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:quality:inspect')")
    public QualityInspection submitQualityInspection(UUID id, QualityInspectionSubmitRequest request,
                                                     String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        requireId(id, "qualityInspectionId");
        if (request == null) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "质检提交请求不能为空");
        }
        return idempotency.execute("mes:quality-inspection:submit", actor.tenantId(), idempotencyKey,
                digest(List.of(id, request)), QualityInspection.class, () -> {
                    QualityInspection current = repository.findInspection(actor.tenantId(), id)
                            .orElseThrow(() -> error(ProductionFactErrorCode.MES_TENANT_001,
                                    "质检不存在或不属于当前租户"));
                    requireInProgressWorkOrder(current.workOrderId());
                    if (current.status() != QualityInspectionStatus.Draft) {
                        throw error(ProductionFactErrorCode.MES_FACT_001, "质检不是 Draft 状态");
                    }
                    BigDecimal qualified = positive(request.qualifiedQty(), "qualifiedQty", false);
                    BigDecimal defect = positive(request.defectQty(), "defectQty", false);
                    if (qualified.add(defect).compareTo(current.sampleQty()) > 0) {
                        throw error(ProductionFactErrorCode.MES_FACT_002, "质检数量超过抽检数");
                    }
                    try {
                        QualityInspection submitted = current.submit(qualified, defect, request.result(),
                                actor.userId(), now());
                        QualityInspection saved = repository.updateInspection(actor.tenantId(), id,
                                value -> value.status() == QualityInspectionStatus.Draft ? submitted
                                        : failStatus("质检已被其他命令提交"));
                        if (saved == null) {
                            throw error(ProductionFactErrorCode.MES_TENANT_001, "质检不存在或不属于当前租户");
                        }
                        synchronizeProgress(actor.tenantId(), saved.workOrderId(), idempotencyKey);
                        return saved;
                    } catch (IllegalArgumentException exception) {
                        throw error(ProductionFactErrorCode.MES_FACT_002, exception.getMessage());
                    }
                });
    }

    @Override
    @PreAuthorize("hasAuthority('mes:quality:inspect')")
    public List<QualityInspection> findQualityInspections(UUID workOrderId) {
        Actor actor = actor();
        requireId(workOrderId, "workOrderId");
        if (!visibleWorkOrder(actor.tenantId(), workOrderId)) {
            return List.of();
        }
        return repository.findInspections(actor.tenantId(), workOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:finished:receipt')")
    public FinishedGoodsReceipt createFinishedGoodsReceipt(FinishedGoodsReceiptCreateRequest request,
                                                           String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        if (request == null) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "成品入库请求不能为空");
        }
        return idempotency.execute("mes:finished-goods-receipt:create", actor.tenantId(), idempotencyKey,
                 digest(request), FinishedGoodsReceipt.class, () -> {
                     requireInProgressWorkOrder(request.workOrderId());
                     BigDecimal quantity = positive(request.receiptQty(), "receiptQty", true);
                     requireId(request.warehouseId(), "warehouseId");
                     requireId(request.locationId(), "locationId");
                     validateFinishedGoodsLocation(actor.tenantId(), request.warehouseId(), request.locationId());
                     repository.lockWorkOrder(actor.tenantId(), request.workOrderId());
                    if (availableFinishedGoods(request.workOrderId()).compareTo(quantity) < 0) {
                        throw finishedGoodsShortage(request.workOrderId(), quantity);
                    }
                    return repository.saveReceipt(FinishedGoodsReceipt.draft(UUID.randomUUID(), actor.tenantId(),
                            requiredText(request.receiptNo(), "receiptNo"), request.workOrderId(), quantity,
                            request.warehouseId(), request.locationId(), actor.userId(), now()));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:finished:confirm')")
    public ProductionFactSummary confirmFinishedGoodsReceipt(UUID id, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        requireId(id, "finishedGoodsReceiptId");
        return idempotency.execute("mes:finished-goods-receipt:confirm", actor.tenantId(), idempotencyKey,
                digest(List.of(id)), ProductionFactSummary.class, () -> {
                    FinishedGoodsReceipt receipt = repository.findReceipt(actor.tenantId(), id)
                            .orElseThrow(() -> error(ProductionFactErrorCode.MES_TENANT_001,
                                    "成品入库不存在或不属于当前租户"));
                    repository.lockWorkOrder(actor.tenantId(), receipt.workOrderId());
                    if (receipt.status() != com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus.Draft) {
                        throw error(ProductionFactErrorCode.MES_FACT_001, "成品入库不是 Draft 状态");
                    }
                    if (availableFinishedGoods(receipt.workOrderId()).compareTo(receipt.receiptQty()) < 0) {
                        throw finishedGoodsShortage(receipt.workOrderId(), receipt.receiptQty());
                    }
                    WorkOrderLifecycle lifecycle = requireInProgressWorkOrder(receipt.workOrderId());
                    InventoryMutationResult result = inventoryCommandService.increase(
                            new InventoryIncreaseCommand(increaseMetadata(actor, receipt.id(), null, idempotencyKey,
                                    "FINISHED_GOODS_RECEIPT", "FINISHED_GOODS_RECEIPT"),
                            new InventoryDimension(lifecycle.workOrder().productId(), receipt.warehouseId(),
                                    receipt.locationId(), ""), receipt.receiptQty()));
                    UUID transactionId = requiredTransaction(result, ProductionFactErrorCode.MES_FG_001);
                    FinishedGoodsReceipt confirmed = repository.updateReceipt(actor.tenantId(), id,
                            current -> current.status() == com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus.Draft
                                    ? current.confirmed(operationId("receipt", current.id()), transactionId,
                                    actor.userId(), actor.sessionId(), now())
                                    : failStatus("成品入库已被其他命令确认"));
                    if (confirmed == null) {
                        throw error(ProductionFactErrorCode.MES_TENANT_001, "成品入库不存在或不属于当前租户");
                    }
                    synchronizeProgress(actor.tenantId(), confirmed.workOrderId(), idempotencyKey);
                    return new ProductionFactSummary("FINISHED_GOODS_RECEIPT", confirmed.id(), confirmed,
                            confirmed.receiptQty(), List.of(transactionId));
                });
    }

    @Override
    @PreAuthorize("hasAuthority('mes:finished:receipt')")
    public List<FinishedGoodsReceipt> findFinishedGoodsReceipts(UUID workOrderId) {
        Actor actor = actor();
        requireId(workOrderId, "workOrderId");
        if (!visibleWorkOrder(actor.tenantId(), workOrderId)) {
            return List.of();
        }
        return repository.findReceipts(actor.tenantId(), workOrderId);
    }

    /** 读取并校验可信租户、用户、会话和请求上下文。 */
    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = RequestContextHolder.getContext().getJti();
        String requestId = RequestContextHolder.getRequestId();
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            throw new ValidationException("缺失可信会话或请求上下文");
        }
        return new Actor(tenantId, userId, sessionId, requestId);
    }

    /** 校验工单属于当前租户，并返回当前生命周期以供产品和计划数量校验。 */
    private WorkOrderLifecycle requireWorkOrder(UUID workOrderId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        WorkOrderLifecycle lifecycle = workOrderService.find(workOrderId).orElseThrow(() ->
                error(ProductionFactErrorCode.MES_TENANT_001, "工单不存在或不属于当前租户"));
        if (lifecycle.workOrder() == null || !tenantId.equals(lifecycle.workOrder().tenantId())) {
            throw error(ProductionFactErrorCode.MES_TENANT_001, "工单不存在或不属于当前租户");
        }
        return lifecycle;
    }

    /** 查询只返回当前租户可见的工单，不把跨租户对象暴露成异常。 */
    private boolean visibleWorkOrder(UUID tenantId, UUID workOrderId) {
        return workOrderService.find(workOrderId)
                .filter(value -> value.workOrder() != null && tenantId.equals(value.workOrder().tenantId()))
                .isPresent();
    }

    /** 生产事实只允许在已下达或生产中工单上登记。 */
    private WorkOrderLifecycle requireExecutableWorkOrder(UUID workOrderId) {
        WorkOrderLifecycle lifecycle = requireWorkOrder(workOrderId);
        if (lifecycle.status() != WorkOrderStatus.Released && lifecycle.status() != WorkOrderStatus.InProgress) {
            throw error(ProductionFactErrorCode.MES_FACT_001, "工单当前状态不允许生产事实操作");
        }
        return lifecycle;
    }

    /** 报工、质检和成品入库必须在真正开始生产后登记，不能用 Released 状态伪造现场事实。 */
    private WorkOrderLifecycle requireInProgressWorkOrder(UUID workOrderId) {
        WorkOrderLifecycle lifecycle = requireWorkOrder(workOrderId);
        if (lifecycle.status() != WorkOrderStatus.InProgress) {
            throw error(ProductionFactErrorCode.MES_FACT_001, "工单必须处于 InProgress 状态才能登记现场事实");
        }
        return lifecycle;
    }

    /**
     * 校验报工引用的工序执行属于当前租户、当前工单和当前工序，并且已经完成。
     * <p>三参数 focused 测试构造器没有该端口；生产 Spring Bean 必须注入真实 PostgreSQL 适配器。</p>
     *
     * @param tenantId 当前可信租户
     * @param executionId 报工引用的工序执行标识
     * @param workOrderId 当前工单标识
     * @param operationId 当前工序标识
     */
    private void validateCompletedOperationExecution(UUID tenantId, UUID executionId,
                                                     UUID workOrderId, UUID operationId) {
        if (operationExecutionRepository == null) {
            return;
        }
        OperationExecution execution = operationExecutionRepository.find(tenantId, executionId)
                .orElseThrow(() -> error(ProductionFactErrorCode.MES_TENANT_001,
                        "工序执行不存在或不属于当前租户"));
        if (!tenantId.equals(execution.tenantId())) {
            throw error(ProductionFactErrorCode.MES_TENANT_001, "工序执行不存在或不属于当前租户");
        }
        if (!workOrderId.equals(execution.workOrderId()) || !operationId.equals(execution.operationId())) {
            throw error(ProductionFactErrorCode.MES_FACT_002,
                    "工序执行与报工工单或工序不一致");
        }
        if (execution.status() != OperationExecutionStatus.Completed) {
            throw error(ProductionFactErrorCode.MES_FACT_001,
                    "只有已完成的工序执行才能报工");
        }
    }

    /**
     * 校验成品入库库位的租户、所属仓库、启用状态和 Storage 类型。
     * <p>旧 focused 测试构造器没有主数据端口；生产 Bean 必须通过 InventoryLocationPort 执行该校验。</p>
     *
     * @param tenantId 当前可信租户
     * @param warehouseId 请求仓库
     * @param locationId 请求库位
     */
    private void validateFinishedGoodsLocation(UUID tenantId, UUID warehouseId, UUID locationId) {
        if (inventoryLocationPort == null) {
            return;
        }
        LocationSnapshot location = inventoryLocationPort.findByTenantIdAndId(tenantId, locationId);
        if (location == null || !tenantId.equals(location.tenantId()) || !locationId.equals(location.id())) {
            throw error(ProductionFactErrorCode.MES_TENANT_001, "成品入库库位不存在或不属于当前租户");
        }
        if (!warehouseId.equals(location.warehouseId())) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "成品入库库位不属于请求仓库");
        }
        if (!location.isActive() || location.type() != LocationType.Storage) {
            throw error(ProductionFactErrorCode.MES_FACT_002,
                    "成品入库库位必须为启用的 Storage 类型");
        }
    }

    /**
     * 校验领料/退料产品必须来自当前工单冻结版本对应的同租户有效 BOM。
     * focused 测试使用三参数构造器时没有 BOM 只读端口，因此保持其内存夹具兼容；生产 Bean 必须注入真实端口。
     */
    private void requireBomMaterials(WorkOrderLifecycle lifecycle, List<UUID> productIds) {
        if (bomFactsPort == null) {
            return;
        }
        BomFact bom = bomFactsPort.findActiveBom(lifecycle.workOrder().tenantId(), lifecycle.workOrder().bomId())
                .filter(value -> value.isActiveFor(lifecycle.workOrder().tenantId(), lifecycle.workOrder().productId()))
                .orElseThrow(() -> error(ProductionFactErrorCode.MES_TENANT_001,
                        "工单 BOM 不存在、已失效、跨租户或产品不一致"));
        Set<UUID> componentProducts = bom.components().stream()
                .map(component -> component.componentProductId()).collect(java.util.stream.Collectors.toSet());
        if (productIds.stream().anyMatch(productId -> !componentProducts.contains(productId))) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "生产物料不在工单 BOM 组件范围内");
        }
    }

    /** 校验领料/退料单头和明细。 */
    private void validateMaterialRequest(String number, UUID workOrderId, List<MaterialItemRequest> items) {
        requiredText(number, "单据编号");
        requireId(workOrderId, "workOrderId");
        if (items == null || items.isEmpty()) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "items 不能为空");
        }
        for (MaterialItemRequest item : items) {
            if (item == null) {
                throw error(ProductionFactErrorCode.MES_FACT_002, "明细不能为空");
            }
            requireId(item.productId(), "productId");
            requireId(item.warehouseId(), "warehouseId");
            requireId(item.locationId(), "locationId");
            positive(item.quantity(), "quantity", true);
        }
    }

    private List<MaterialIssueLine> issueLines(List<MaterialItemRequest> items) {
        List<MaterialIssueLine> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MaterialItemRequest item = items.get(i);
            lines.add(new MaterialIssueLine(UUID.randomUUID(), i + 1, item.productId(), item.warehouseId(),
                    item.locationId(), item.quantity(), null));
        }
        return List.copyOf(lines);
    }

    private List<MaterialReturnLine> returnLines(List<MaterialItemRequest> items) {
        List<MaterialReturnLine> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MaterialItemRequest item = items.get(i);
            lines.add(new MaterialReturnLine(UUID.randomUUID(), i + 1, item.productId(), item.warehouseId(),
                    item.locationId(), item.quantity(), null));
        }
        return List.copyOf(lines);
    }

    /** 计算工单同产品已领未退数量；退料目标库位可与原领料库位不同。 */
    private BigDecimal returnableQuantity(UUID tenantId, UUID workOrderId, UUID productId) {
        BigDecimal issued = repository.findIssues(tenantId, workOrderId).stream()
                .filter(value -> value.status() == MaterialDocumentStatus.Confirmed)
                .flatMap(value -> value.lines().stream())
                .filter(line -> line.productId().equals(productId))
                .map(MaterialIssueLine::issueQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = repository.findReturns(tenantId, workOrderId).stream()
                .filter(value -> value.status() == MaterialDocumentStatus.Confirmed)
                .flatMap(value -> value.lines().stream())
                .filter(line -> line.productId().equals(productId))
                .map(MaterialReturnLine::returnQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        return issued.subtract(returned).max(BigDecimal.ZERO);
    }

    /** 计算 Passed 质检合格数减已确认成品入库数。 */
    private BigDecimal availableFinishedGoods(UUID workOrderId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        BigDecimal qualified = repository.findInspections(tenantId, workOrderId).stream()
                .filter(value -> value.status() == QualityInspectionStatus.Passed)
                .map(QualityInspection::qualifiedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = repository.findReceipts(tenantId, workOrderId).stream()
                .filter(value -> value.status() == com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus.Confirmed)
                .map(FinishedGoodsReceipt::receiptQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        return qualified.subtract(received).max(BigDecimal.ZERO);
    }

    private ProductionFactException finishedGoodsShortage(UUID workOrderId, BigDecimal requested) {
        boolean failed = repository.findInspections(TenantContextHolder.requireTenantId(), workOrderId).stream()
                .anyMatch(value -> value.status() == QualityInspectionStatus.Failed);
        return error(failed ? ProductionFactErrorCode.MES_QC_001 : ProductionFactErrorCode.MES_FG_001,
                failed ? "存在 Failed 质检，不能将对应数量直接入库" : "可入库合格数量不足: " + requested);
    }

    /**
     * 将生产事实汇总同步给工单生命周期端口，供完成判断恢复使用；该快照不替代明细事实。
     * <p>只有 InProgress 工单参与同步，Draft/Released 的 focused 夹具不会被隐式推进状态。</p>
     */
    private void synchronizeProgress(UUID tenantId, UUID workOrderId, String parentKey) {
        WorkOrderLifecycle lifecycle = workOrderService.find(workOrderId).orElse(null);
        if (lifecycle == null || lifecycle.status() != WorkOrderStatus.InProgress) {
            return;
        }
        BigDecimal reported = repository.findReports(tenantId, workOrderId).stream()
                .map(WorkReport::reportQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal qualified = repository.findReports(tenantId, workOrderId).stream()
                .map(WorkReport::qualifiedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal defect = repository.findReports(tenantId, workOrderId).stream()
                .map(WorkReport::defectQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = repository.findReceipts(tenantId, workOrderId).stream()
                .filter(value -> value.status() == com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus.Confirmed)
                .map(FinishedGoodsReceipt::receiptQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean qualityBlocked = repository.findInspections(tenantId, workOrderId).stream()
                .anyMatch(value -> value.status() == QualityInspectionStatus.Failed
                        || value.status() == QualityInspectionStatus.Draft
                        || value.status() == QualityInspectionStatus.Submitted);
        WorkOrderProgress progress = new WorkOrderProgress(
                new HashSet<>(lifecycle.progress().completedOperationIds()), reported, qualified, defect,
                received, qualityBlocked, false);
        workOrderService.recordProgress(workOrderId, progress,
                "mes-progress-" + UUID.nameUUIDFromBytes((parentKey + "|" + workOrderId)
                        .getBytes(StandardCharsets.UTF_8)));
    }

    private InventoryCommandMetadata decreaseMetadata(Actor actor, UUID sourceId, UUID lineId, String key) {
        return metadata(actor, sourceId, lineId, key, "MATERIAL_ISSUE", "MATERIAL_ISSUE");
    }

    private InventoryCommandMetadata increaseMetadata(Actor actor, UUID sourceId, UUID lineId, String key,
                                                      String sourceType, String transactionType) {
        return metadata(actor, sourceId, lineId, key, transactionType, sourceType);
    }

    private InventoryCommandMetadata metadata(Actor actor, UUID sourceId, UUID lineId, String key,
                                              String transactionType, String sourceType) {
        return new InventoryCommandMetadata(actor.tenantId(), actor.userId(), actor.sessionId(), actor.requestId(),
                childKey(key, sourceId, lineId), digest(new InventoryDigestPayload(sourceId, lineId, transactionType)), sourceType,
                sourceId, lineId, transactionType, now());
    }

    private UUID requiredTransaction(InventoryMutationResult result, ProductionFactErrorCode code) {
        if (result == null || result.transactions() == null || result.transactions().isEmpty()
                || result.transactions().getFirst() == null || result.transactions().getFirst().id() == null) {
            throw error(code, "库存端口未返回库存流水标识");
        }
        return result.transactions().getFirst().id();
    }

    private MaterialIssue requiredIssue(UUID tenantId, UUID id) {
        return repository.findIssue(tenantId, id).orElseThrow(() ->
                error(ProductionFactErrorCode.MES_TENANT_001, "领料单不存在或不属于当前租户"));
    }

    private MaterialReturn requiredReturn(UUID tenantId, UUID id) {
        return repository.findReturn(tenantId, id).orElseThrow(() ->
                error(ProductionFactErrorCode.MES_TENANT_001, "退料单不存在或不属于当前租户"));
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw error(ProductionFactErrorCode.MES_FACT_002, field + " 不能为空或长度超过 64");
        }
        return value;
    }

    private OffsetDateTime requiredTime(OffsetDateTime value) {
        if (value == null) {
            throw error(ProductionFactErrorCode.MES_FACT_002, "reportTime 不能为空");
        }
        return value;
    }

    private BigDecimal positive(BigDecimal value, String field, boolean strictlyPositive) {
        if (value == null || value.scale() > 6 || value.signum() < 0
                || (strictlyPositive && value.signum() == 0)) {
            throw error(ProductionFactErrorCode.MES_FACT_002, field + " 数量不合法");
        }
        return value;
    }

    private void requireId(UUID value, String field) {
        if (value == null) {
            throw error(ProductionFactErrorCode.MES_FACT_002, field + " 不能为空");
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    private String childKey(String parent, UUID sourceId, UUID lineId) {
        return "mes-fact-" + UUID.nameUUIDFromBytes((parent + "|" + sourceId + "|" + lineId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private UUID operationId(String type, UUID factId) {
        return UUID.nameUUIDFromBytes(("mes-fact|" + type + "|" + factId).getBytes(StandardCharsets.UTF_8));
    }

    private String digest(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("生产事实幂等摘要生成失败", exception);
        }
    }

    private ProductionFactException error(ProductionFactErrorCode code, String detail) {
        return new ProductionFactException(code, detail);
    }

    private <T> T failStatus(String detail) {
        throw error(ProductionFactErrorCode.MES_FACT_001, detail);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }

    /** 库存子命令摘要载荷；明细 ID 可空（例如成品入库单头）。 */
    private record InventoryDigestPayload(UUID sourceId, UUID sourceLineId, String transactionType) {
    }
}
