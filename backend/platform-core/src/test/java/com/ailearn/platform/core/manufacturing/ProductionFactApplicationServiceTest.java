package com.ailearn.platform.core.manufacturing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.productionfact.application.ProductionFactApplicationServiceImpl;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialDocumentStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactSummary;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspectionStatus;
import com.ailearn.platform.core.manufacturing.productionfact.dto.FinishedGoodsReceiptCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialIssueCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialItemRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.MaterialReturnCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.QualityInspectionSubmitRequest;
import com.ailearn.platform.core.manufacturing.productionfact.dto.WorkReportCreateRequest;
import com.ailearn.platform.core.manufacturing.productionfact.exception.ProductionFactException;
import com.ailearn.platform.core.manufacturing.productionfact.infrastructure.InMemoryProductionFactRepository;
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
 * S5 Task16 生产事实应用层 focused tests。
 * <p>
 * 不连接数据库，使用内存事实端口和库存应用端口 mock，验证可信上下文、幂等、数量/状态规则以及库存写入边界。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ProductionFactApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b5000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER_ID = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("d5000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("e5000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION_ID = UUID.fromString("f5000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("a5000000-0000-0000-0000-000000000002");
    private static final UUID EXECUTION_ID = UUID.fromString("b5000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private InventoryCommandService inventoryCommandService;

    @Mock
    private WorkOrderExecutionService workOrderService;

    private ProductionFactApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti("task16-session");
        RequestContextHolder.getContext().setRequestId("task16-request");
        WorkOrderFact workOrder = new WorkOrderFact(WORK_ORDER_ID, TENANT_ID, "WO-TASK16", PRODUCT_ID,
                new BigDecimal("100"), NOW.minusHours(1), NOW.plusHours(8), UUID.randomUUID(), "V1",
                UUID.randomUUID(), "V1", null, WorkOrderStatus.InProgress, false, USER_ID, NOW);
        WorkOrderLifecycle lifecycle = new WorkOrderLifecycle(workOrder, WorkOrderStatus.InProgress,
                Set.of(OPERATION_ID), WorkOrderProgress.empty(), "V1", "V1", USER_ID, NOW,
                USER_ID, NOW, null, null, null, null, null, null);
        when(workOrderService.find(WORK_ORDER_ID)).thenReturn(Optional.of(lifecycle));
        service = new ProductionFactApplicationServiceImpl(new InMemoryProductionFactRepository(),
                inventoryCommandService, workOrderService);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    /** 领料和退料必须通过库存端口写入，并且重复创建幂等重放不重复保存事实。 */
    @Test
    void materialIssueAndReturnUseInventoryPortAndIdempotency() {
        when(inventoryCommandService.decrease(any())).thenReturn(mutation("issue-transaction"));
        when(inventoryCommandService.increase(any())).thenReturn(mutation("return-transaction"));

        MaterialIssueCreateRequest issueRequest = new MaterialIssueCreateRequest("MI-001", WORK_ORDER_ID,
                List.of(item("5")));
        var first = service.createMaterialIssue(issueRequest, "issue-create-key");
        var replay = service.createMaterialIssue(issueRequest, "issue-create-key");
        assertEquals(first, replay);

        ProductionFactSummary issueResult = service.confirmMaterialIssue(first.id(), "issue-confirm-key");
        assertEquals(MaterialDocumentStatus.Confirmed, ((com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue)
                issueResult.fact()).status());

        var materialReturn = service.createMaterialReturn(
                new MaterialReturnCreateRequest("MR-001", WORK_ORDER_ID, List.of(item("2"))),
                "return-create-key");
        ProductionFactSummary returnResult = service.confirmMaterialReturn(materialReturn.id(), "return-confirm-key");

        assertEquals(1, issueResult.inventoryTransactionIds().size());
        assertEquals(1, returnResult.inventoryTransactionIds().size());
        verify(inventoryCommandService, times(1)).decrease(any());
        verify(inventoryCommandService, times(1)).increase(any());
    }

    /** Draft 退料在创建后若被其他单据耗尽可退量，确认时必须再次拒绝，不能产生库存增加。 */
    @Test
    void returnRechecksAvailableQuantityAtConfirmation() {
        when(inventoryCommandService.decrease(any())).thenReturn(mutation("issue-transaction"));
        when(inventoryCommandService.increase(any())).thenReturn(mutation("return-transaction"));

        var issue = service.createMaterialIssue(new MaterialIssueCreateRequest("MI-002", WORK_ORDER_ID,
                List.of(item("5"))), "issue-2-create");
        service.confirmMaterialIssue(issue.id(), "issue-2-confirm");
        var firstReturn = service.createMaterialReturn(new MaterialReturnCreateRequest("MR-002", WORK_ORDER_ID,
                List.of(item("5"))), "return-2-create");
        var secondReturn = service.createMaterialReturn(new MaterialReturnCreateRequest("MR-003", WORK_ORDER_ID,
                List.of(item("5"))), "return-3-create");
        service.confirmMaterialReturn(firstReturn.id(), "return-2-confirm");

        ProductionFactException exception = assertThrows(ProductionFactException.class,
                () -> service.confirmMaterialReturn(secondReturn.id(), "return-3-confirm"));
        assertEquals("MES_MAT_002", exception.getBusinessCode());
        verify(inventoryCommandService, times(1)).increase(any());
    }

    /** 报工、Passed 质检和成品入库组成闭环，成品库存增加必须经库存应用端口。 */
    @Test
    void workReportQualityAndFinishedReceiptFormProductionFactChain() {
        when(inventoryCommandService.increase(any())).thenReturn(mutation("finished-transaction"));

        var report = service.createWorkReport(new WorkReportCreateRequest("WR-001", EXECUTION_ID,
                WORK_ORDER_ID, OPERATION_ID, NOW, new BigDecimal("10"), BigDecimal.ZERO, "首件"),
                "report-create-key");
        var inspection = service.createQualityInspection(new QualityInspectionCreateRequest("QI-001",
                report.id(), "FINAL", new BigDecimal("10")), "inspection-create-key");
        var submitted = service.submitQualityInspection(inspection.id(),
                new QualityInspectionSubmitRequest(new BigDecimal("10"), BigDecimal.ZERO, "PASSED"),
                "inspection-submit-key");

        var receipt = service.createFinishedGoodsReceipt(new FinishedGoodsReceiptCreateRequest("FG-001",
                WORK_ORDER_ID, new BigDecimal("10"), WAREHOUSE_ID, LOCATION_ID), "receipt-create-key");
        ProductionFactSummary result = service.confirmFinishedGoodsReceipt(receipt.id(), "receipt-confirm-key");

        assertEquals(new BigDecimal("10"), report.reportQty());
        assertEquals(QualityInspectionStatus.Passed.name(), submitted.result());
        assertEquals(1, result.inventoryTransactionIds().size());
        verify(inventoryCommandService, times(1)).increase(any());
    }

    /** 跨租户工单不可登记任何生产事实，且在库存端口之前失败。 */
    @Test
    void missingTenantWorkOrderIsRejectedBeforeInventoryMutation() {
        when(workOrderService.find(WORK_ORDER_ID)).thenReturn(Optional.empty());
        ProductionFactException exception = assertThrows(ProductionFactException.class,
                () -> service.createMaterialIssue(new MaterialIssueCreateRequest("MI-003", WORK_ORDER_ID,
                        List.of(item("1"))), "cross-tenant-key"));

        assertEquals("MES_TENANT_001", exception.getBusinessCode());
        verify(inventoryCommandService, times(0)).decrease(any());
    }

    /** 构造不含客户端租户和审计字段的库存维度请求明细。 */
    private MaterialItemRequest item(String quantity) {
        return new MaterialItemRequest(PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, new BigDecimal(quantity));
    }

    /** 构造携带库存流水事实的端口结果。 */
    private InventoryMutationResult mutation(String transactionNo) {
        InventoryTransaction transaction = new InventoryTransaction(UUID.randomUUID(), TENANT_ID,
                transactionNo, "MES", "MES", WORK_ORDER_ID, null, null, null, BigDecimal.ONE, NOW,
                USER_ID, "task16-session", "task16-request", transactionNo, "digest");
        return new InventoryMutationResult("test", BigDecimal.ONE, List.of(), null, List.of(),
                List.of(transaction), Set.of());
    }
}
