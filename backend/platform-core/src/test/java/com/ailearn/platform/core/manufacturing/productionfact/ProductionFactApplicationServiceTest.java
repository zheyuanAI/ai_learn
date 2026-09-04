package com.ailearn.platform.core.manufacturing.productionfact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.productionfact.application.ProductionFactApplicationServiceImpl;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialDocumentStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactSummary;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspectionStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import com.ailearn.platform.core.manufacturing.productionfact.dto.FinishedGoodsReceiptCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialIssueCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialItemRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionSubmitRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.WorkReportCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.exception.ProductionFactException;
import com.ailearn.platform.core.manufacturing.productionfact.infrastructure.InMemoryProductionFactRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * S5 Task16 focused tests。
 * <p>
 * 使用 Task16 内存事实端口和既有库存/工单应用端口 mock，不连接数据库，重点验证状态、数量、租户和幂等边界。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductionFactApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("a5000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b5000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID MATERIAL_ID = UUID.fromString("c5000000-0000-0000-0000-000000000002");
    private static final UUID WAREHOUSE_ID = UUID.fromString("d5000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION_ID = UUID.fromString("e5000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER_ID = UUID.fromString("f5000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_EXECUTION_ID = UUID.fromString("f5000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION_ID = UUID.fromString("f5000000-0000-0000-0000-000000000003");
    private static final OffsetDateTime TIME = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private InventoryCommandService inventoryCommandService;
    @Mock
    private WorkOrderExecutionService workOrderService;

    private InMemoryProductionFactRepository repository;
    private ProductionFactApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti("jti-task16-test");
        RequestContextHolder.getContext().setRequestId("request-task16-test");
        repository = new InMemoryProductionFactRepository();
        service = new ProductionFactApplicationServiceImpl(repository, inventoryCommandService, workOrderService);
        when(workOrderService.find(WORK_ORDER_ID)).thenReturn(Optional.of(workOrder(TENANT_ID, WorkOrderStatus.InProgress)));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    /** 领料确认必须扣减库存，重复使用同一幂等键只返回首次事实。 */
    @Test
    void materialIssueConfirmsThroughInventoryAndReplaysIdempotently() {
        when(inventoryCommandService.decrease(any())).thenReturn(mutation("MATERIAL_ISSUE"));
        MaterialIssue draft = service.createMaterialIssue(new MaterialIssueCreateRequest("MI-1", WORK_ORDER_ID,
                List.of(new MaterialItemRequest(MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, qty("2")))), "issue-create");

        ProductionFactSummary first = service.confirmMaterialIssue(draft.id(), "issue-confirm");
        ProductionFactSummary replay = service.confirmMaterialIssue(draft.id(), "issue-confirm");

        assertEquals(MaterialDocumentStatus.Confirmed, ((MaterialIssue) first.fact()).status());
        assertEquals(first.factId(), replay.factId());
        assertEquals(first.inventoryTransactionIds(), replay.inventoryTransactionIds());
        verify(inventoryCommandService).decrease(any());
    }

    /** 退料不能超过当前工单已确认领料减已确认退料数量。 */
    @Test
    void materialReturnRejectsQuantityBeyondIssuedBalance() {
        when(inventoryCommandService.decrease(any())).thenReturn(mutation("MATERIAL_ISSUE"));
        MaterialIssue issue = service.createMaterialIssue(new MaterialIssueCreateRequest("MI-2", WORK_ORDER_ID,
                List.of(new MaterialItemRequest(MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, qty("2")))), "issue-create-2");
        service.confirmMaterialIssue(issue.id(), "issue-confirm-2");

        ProductionFactException exception = assertThrows(ProductionFactException.class,
                () -> service.createMaterialReturn(new com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialReturnCreateRequest(
                        "MR-1", WORK_ORDER_ID,
                        List.of(new MaterialItemRequest(MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, qty("3")))),
                        "return-create-1"));

        assertEquals("MES_MAT_002", exception.getBusinessCode());
        verify(inventoryCommandService, never()).increase(any());
    }

    /** 报工总量受工单计划约束，Passed 质检后成品入库只能增加合格数量。 */
    @Test
    void reportQualityAndFinishedGoodsReceiptCloseTheFactChain() {
        WorkReport report = service.createWorkReport(new WorkReportCreateRequest("WR-1", OPERATION_EXECUTION_ID,
                WORK_ORDER_ID, OPERATION_ID, TIME, qty("5"), qty("1"), "首件"), "report-create");
        QualityInspection inspection = service.createQualityInspection(new QualityInspectionCreateRequest(
                "QC-1", report.id(), "FINAL", qty("5")), "qc-create");
        QualityInspection passed = service.submitQualityInspection(inspection.id(),
                new QualityInspectionSubmitRequest(qty("5"), qty("0"), "PASSED"), "qc-submit");
        assertEquals(QualityInspectionStatus.Passed, passed.status());

        when(inventoryCommandService.increase(any())).thenReturn(mutation("FINISHED_GOODS_RECEIPT"));
        FinishedGoodsReceipt receipt = service.createFinishedGoodsReceipt(
                new FinishedGoodsReceiptCreateRequest("FG-1", WORK_ORDER_ID, qty("5"), WAREHOUSE_ID, LOCATION_ID),
                "fg-create");
        ProductionFactSummary result = service.confirmFinishedGoodsReceipt(receipt.id(), "fg-confirm");

        assertEquals(0, result.quantity().compareTo(qty("5")));
        assertEquals(1, result.inventoryTransactionIds().size());
        verify(inventoryCommandService).increase(any());
    }

    /** Failed 质检不得直接创建对应成品入库，且跨租户查询隐藏事实。 */
    @Test
    void failedQualityBlocksFinishedGoodsAndCrossTenantCannotRead() {
        WorkReport report = service.createWorkReport(new WorkReportCreateRequest("WR-2", OPERATION_EXECUTION_ID,
                WORK_ORDER_ID, OPERATION_ID, TIME, qty("3"), qty("1"), null), "report-create-2");
        QualityInspection inspection = service.createQualityInspection(new QualityInspectionCreateRequest(
                "QC-2", report.id(), "FINAL", qty("4")), "qc-create-2");
        service.submitQualityInspection(inspection.id(),
                new QualityInspectionSubmitRequest(qty("3"), qty("1"), "FAILED"), "qc-submit-2");

        ProductionFactException exception = assertThrows(ProductionFactException.class,
                () -> service.createFinishedGoodsReceipt(new FinishedGoodsReceiptCreateRequest(
                        "FG-2", WORK_ORDER_ID, qty("1"), WAREHOUSE_ID, LOCATION_ID), "fg-create-2"));
        assertEquals("MES_QC_001", exception.getBusinessCode());

        TenantContextHolder.setTenantId(OTHER_TENANT);
        assertTrue(service.findWorkReports(WORK_ORDER_ID).isEmpty());
    }

    /** 累计报工超过工单计划数量时，必须在保存事实前返回 MES_WO_003。 */
    @Test
    void workReportRejectsQuantityBeyondWorkOrderPlan() {
        ProductionFactException exception = assertThrows(ProductionFactException.class,
                () -> service.createWorkReport(new WorkReportCreateRequest("WR-OVER", OPERATION_EXECUTION_ID,
                        WORK_ORDER_ID, OPERATION_ID, TIME, qty("10.1"), BigDecimal.ZERO, null),
                        "report-over-plan"));

        assertEquals("MES_WO_003", exception.getBusinessCode());
    }

    /** 工单跨租户时所有 Task16 写入都在库存调用前拒绝。 */
    @Test
    void crossTenantWorkOrderIsRejectedBeforeInventoryMutation() {
        when(workOrderService.find(WORK_ORDER_ID)).thenReturn(Optional.of(workOrder(OTHER_TENANT,
                WorkOrderStatus.InProgress)));

        ProductionFactException exception = assertThrows(ProductionFactException.class,
                () -> service.createMaterialIssue(new MaterialIssueCreateRequest("MI-TENANT", WORK_ORDER_ID,
                        List.of(new MaterialItemRequest(MATERIAL_ID, WAREHOUSE_ID, LOCATION_ID, qty("1")))),
                        "tenant-create"));

        assertEquals("MES_TENANT_001", exception.getBusinessCode());
        verify(inventoryCommandService, never()).decrease(any());
    }

    private WorkOrderLifecycle workOrder(UUID tenantId, WorkOrderStatus status) {
        WorkOrderFact fact = new WorkOrderFact(WORK_ORDER_ID, tenantId, "WO-1", PRODUCT_ID, qty("10"),
                TIME, TIME.plusHours(8), UUID.randomUUID(), "B1", UUID.randomUUID(), "R1", null,
                status, false, USER_ID, TIME);
        return new WorkOrderLifecycle(fact, status, Set.of(OPERATION_ID),
                com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress.empty(),
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private InventoryMutationResult mutation(String operation) {
        InventoryTransaction transaction = new InventoryTransaction(UUID.randomUUID(), TENANT_ID, "INV-1",
                operation, "MES", WORK_ORDER_ID, null, null, null, qty("1"), TIME, USER_ID,
                "jti-task16-test", "request-task16-test", operation, "digest");
        return new InventoryMutationResult(operation, qty("1"), List.of(), null, List.of(),
                List.of(transaction), Set.of());
    }

    private BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
