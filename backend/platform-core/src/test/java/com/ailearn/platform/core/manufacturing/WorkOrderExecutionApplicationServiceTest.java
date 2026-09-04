package com.ailearn.platform.core.manufacturing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionServiceImpl;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderCompletionType;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.execution.exception.WorkOrderExecutionException;
import com.ailearn.platform.core.manufacturing.execution.infrastructure.InMemoryWorkOrderLifecycleRepository;
import com.ailearn.platform.core.manufacturing.foundation.application.ManufacturingFoundationServiceImpl;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomComponentFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingOperationFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.SalesLineFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.infrastructure.InMemoryFoundationRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Task 14 工单生命周期 focused tests。
 * <p>
 * 测试使用 foundation 既有内存端口，不连接数据库；只验证 BOM/Routing 版本、工单状态、销售来源和人工完成语义。
 * </p>
 */
class WorkOrderExecutionApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("a2000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("c2000000-0000-0000-0000-000000000001");
    private static final UUID COMPONENT_ID = UUID.fromString("c2000000-0000-0000-0000-000000000002");
    private static final UUID SALES_LINE_ID = UUID.fromString("d2000000-0000-0000-0000-000000000001");
    private static final UUID WORK_CENTER_ID = UUID.fromString("e2000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-09-04T08:00:00Z");
    private static final OffsetDateTime FINISH = OffsetDateTime.parse("2026-09-04T16:00:00Z");

    private InMemoryFoundationRepository foundationRepository;
    private WorkOrderExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        setContext(TENANT_A);
        foundationRepository = new InMemoryFoundationRepository();
        ManufacturingFoundationServiceImpl foundationService = new ManufacturingFoundationServiceImpl(
                foundationRepository);
        service = new WorkOrderExecutionServiceImpl(foundationService,
                new InMemoryWorkOrderLifecycleRepository(), foundationRepository, foundationRepository,
                foundationRepository);
        foundationRepository.saveSalesLine(new SalesLineFact(SALES_LINE_ID, TENANT_A, PRODUCT_ID,
                new BigDecimal("100"), true));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    /** 验证工单完整状态路径、审核时版本锁定以及销售来源保留。 */
    @Test
    void followsApprovalExecutionAndNormalCompletionPath() {
        BomFact bom = createBom("V1", BomStatus.ACTIVE);
        RoutingFact routing = createRouting("V2", RoutingStatus.ACTIVE);
        WorkOrderLifecycle created = service.createWorkOrder(workOrder(bom, routing, SALES_LINE_ID), "create-path");

        assertEquals(WorkOrderStatus.Draft, created.status());
        assertEquals(SALES_LINE_ID, created.workOrder().sourceSalesOrderLineId());
        assertTrue(created.allowedActions().contains("submit"));

        WorkOrderLifecycle pending = service.submit(created.workOrder().id(), "submit-path");
        assertEquals(WorkOrderStatus.PendingApproval, pending.status());
        WorkOrderLifecycle released = service.approve(created.workOrder().id(), "approve-path");
        assertEquals(WorkOrderStatus.Released, released.status());
        assertEquals("V1", released.lockedBomVersion());
        assertEquals("V2", released.lockedRoutingVersion());
        assertEquals(USER_ID, released.reviewedBy());

        WorkOrderLifecycle inProgress = service.startProduction(created.workOrder().id(), "start-path");
        assertEquals(WorkOrderStatus.InProgress, inProgress.status());
        UUID operationId = routing.operations().get(0).id();
        WorkOrderLifecycle progressed = service.recordProgress(created.workOrder().id(),
                new WorkOrderProgress(Set.of(operationId), new BigDecimal("10"),
                        new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("10"), false, false),
                "progress-path");
        WorkOrderLifecycle completed = service.complete(created.workOrder().id(), "complete-path");

        assertEquals(progressed.progress(), completed.progress());
        assertEquals(WorkOrderStatus.Completed, completed.status());
        assertEquals(WorkOrderCompletionType.Normal, completed.completionType());
        assertTrue(completed.allowedActions().isEmpty());
    }

    /** 审核拒绝必须有原因，拒绝后可重新提交，待审核期间不能重复提交。 */
    @Test
    void rejectsWithoutReasonAndAllowsRejectedResubmission() {
        BomFact bom = createBom("V1", BomStatus.ACTIVE);
        RoutingFact routing = createRouting("V1", RoutingStatus.ACTIVE);
        WorkOrderLifecycle created = service.createWorkOrder(workOrder(bom, routing, null), "create-reject");
        service.submit(created.workOrder().id(), "submit-reject");

        WorkOrderExecutionException noReason = assertThrows(WorkOrderExecutionException.class,
                () -> service.reject(created.workOrder().id(), " ", "reject-empty"));
        assertEquals("MES_WO_005", noReason.getBusinessCode());

        WorkOrderLifecycle rejected = service.reject(created.workOrder().id(), "数量需要调整", "reject-valid");
        assertEquals(WorkOrderStatus.Rejected, rejected.status());
        assertEquals("数量需要调整", rejected.rejectionReason());
        WorkOrderLifecycle resubmitted = service.submit(created.workOrder().id(), "resubmit-reject");
        assertEquals(WorkOrderStatus.PendingApproval, resubmitted.status());
        assertEquals("数量需要调整", resubmitted.rejectionReason());
    }

    /** 正常完成必须满足工序、报工、质检和入库汇总，超计划报工直接拒绝。 */
    @Test
    void normalCompletionRejectsIncompleteProgress() {
        BomFact bom = createBom("V1", BomStatus.ACTIVE);
        RoutingFact routing = createRouting("V1", RoutingStatus.ACTIVE);
        WorkOrderLifecycle created = service.createWorkOrder(workOrder(bom, routing, null), "create-progress");
        service.submit(created.workOrder().id(), "submit-progress");
        service.approve(created.workOrder().id(), "approve-progress");
        service.startProduction(created.workOrder().id(), "start-progress");

        WorkOrderExecutionException overPlan = assertThrows(WorkOrderExecutionException.class,
                () -> service.recordProgress(created.workOrder().id(),
                        new WorkOrderProgress(Set.of(), new BigDecimal("11"),
                                new BigDecimal("11"), BigDecimal.ZERO, BigDecimal.ZERO, false, false),
                        "over-plan"));
        assertEquals("MES_WO_003", overPlan.getBusinessCode());

        WorkOrderExecutionException incomplete = assertThrows(WorkOrderExecutionException.class,
                () -> service.complete(created.workOrder().id(), "complete-incomplete"));
        assertEquals("MES_WO_001", incomplete.getBusinessCode());
    }

    /** 多道工序先后开工时，首道工序推进工单，后续工序不得因工单已 InProgress 被错误拦截。 */
    @Test
    void startingProductionAgainKeepsInProgressState() {
        BomFact bom = createBom("V1", BomStatus.ACTIVE);
        RoutingFact routing = createRouting("V1", RoutingStatus.ACTIVE);
        WorkOrderLifecycle created = service.createWorkOrder(workOrder(bom, routing, null), "create-repeat-start");
        service.submit(created.workOrder().id(), "submit-repeat-start");
        service.approve(created.workOrder().id(), "approve-repeat-start");

        WorkOrderLifecycle first = service.startProduction(created.workOrder().id(), "start-repeat-1");
        WorkOrderLifecycle second = service.startProduction(created.workOrder().id(), "start-repeat-2");

        assertEquals(WorkOrderStatus.InProgress, first.status());
        assertEquals(WorkOrderStatus.InProgress, second.status());
    }

    /** 人工完成只写审计字段，不补造工序、报工、质检或成品入库数量。 */
    @Test
    void manualCompletionPreservesExistingProgressAndIsIdempotent() {
        BomFact bom = createBom("V1", BomStatus.ACTIVE);
        RoutingFact routing = createRouting("V1", RoutingStatus.ACTIVE);
        WorkOrderLifecycle created = service.createWorkOrder(workOrder(bom, routing, null), "create-manual");
        service.submit(created.workOrder().id(), "submit-manual");
        service.approve(created.workOrder().id(), "approve-manual");

        WorkOrderLifecycle completed = service.manualComplete(created.workOrder().id(),
                "客户取消剩余数量", "manual-complete");
        WorkOrderLifecycle replay = service.manualComplete(created.workOrder().id(),
                "客户取消剩余数量", "manual-complete");

        assertEquals(WorkOrderStatus.Completed, completed.status());
        assertEquals(WorkOrderCompletionType.Manual, completed.completionType());
        assertEquals("客户取消剩余数量", completed.completionReason());
        assertEquals(WorkOrderProgress.empty(), completed.progress());
        assertEquals(completed, replay);
        assertFalse(completed.progress().pendingInventoryCommands());
    }

    /** 跨租户工单不可查询或推进，来源销售明细继续由 foundation 端口做租户隔离。 */
    @Test
    void hidesLifecycleFromAnotherTenant() {
        BomFact bom = createBom("V1", BomStatus.ACTIVE);
        RoutingFact routing = createRouting("V1", RoutingStatus.ACTIVE);
        WorkOrderLifecycle created = service.createWorkOrder(workOrder(bom, routing, SALES_LINE_ID), "create-tenant");

        setContext(TENANT_B);
        assertTrue(service.find(created.workOrder().id()).isEmpty());
        WorkOrderExecutionException exception = assertThrows(WorkOrderExecutionException.class,
                () -> service.submit(created.workOrder().id(), "submit-cross-tenant"));
        assertEquals("MES_TENANT_001", exception.getBusinessCode());
    }

    /** 创建有效 BOM/Routing，供每个状态场景使用同一个 foundation 事实源。 */
    private BomFact createBom(String version, BomStatus status) {
        return foundationRepository.saveBom(new BomFact(UUID.randomUUID(), TENANT_A, PRODUCT_ID,
                "BOM-EXEC-" + version + "-" + UUID.randomUUID(), version, status,
                List.of(new BomComponentFact(COMPONENT_ID, new BigDecimal("2"), "PCS", null)),
                false, USER_ID, OffsetDateTime.now()));
    }

    /** 创建包含一道必需工序的 Routing 事实。 */
    private RoutingFact createRouting(String version, RoutingStatus status) {
        return foundationRepository.saveRouting(new RoutingFact(UUID.randomUUID(), TENANT_A, PRODUCT_ID,
                "ROUTING-EXEC-" + version + "-" + UUID.randomUUID(), version, status,
                List.of(new RoutingOperationFact(UUID.randomUUID(), 10, "装配", WORK_CENTER_ID,
                        new BigDecimal("30"))), false,
                USER_ID, OffsetDateTime.now()));
    }

    /** 构造不携带租户字段的 foundation 工单创建请求。 */
    private WorkOrderCreateRequest workOrder(BomFact bom, RoutingFact routing, UUID sourceSalesLineId) {
        return new WorkOrderCreateRequest(null, PRODUCT_ID, new BigDecimal("10"), START, FINISH,
                bom.id(), routing.id(), sourceSalesLineId);
    }

    /** 准备可信租户、用户和会话上下文。 */
    private void setContext(UUID tenantId) {
        TenantContextHolder.setTenantId(tenantId);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti("mes-execution-test-jti");
        RequestContextHolder.getContext().setRequestId("mes-execution-test-request");
    }
}
