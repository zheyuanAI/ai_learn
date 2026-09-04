package com.ailearn.platform.core.manufacturing.task15;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.core.manufacturing.contextquery.domain.ProductionContext;
import com.ailearn.platform.core.manufacturing.contextquery.exception.ProductionContextException;
import com.ailearn.platform.core.manufacturing.contextquery.infrastructure.InMemoryProductionContextQuery;
import com.ailearn.platform.core.manufacturing.dispatch.application.DispatchApplicationServiceImpl;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchStatus;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchCreateRequest;
import com.ailearn.platform.core.manufacturing.dispatch.exception.DispatchException;
import com.ailearn.platform.core.manufacturing.dispatch.infrastructure.InMemoryDispatchRepository;
import com.ailearn.platform.core.manufacturing.operation.application.OperationExecutionApplicationServiceImpl;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionEventType;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionStatus;
import com.ailearn.platform.core.manufacturing.operation.dto.OperationExecutionCreateRequest;
import com.ailearn.platform.core.manufacturing.operation.exception.OperationExecutionException;
import com.ailearn.platform.core.manufacturing.operation.infrastructure.InMemoryOperationExecutionRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S5 Task15 派工、工序执行和生产上下文内部端口测试。 */
class Task15DispatchOperationContextTest {
    private static final UUID TENANT_A = UUID.fromString("a5000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("a5000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("b5000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER = UUID.fromString("c5000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION = UUID.fromString("d5000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE = UUID.fromString("e5000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-09-04T08:00:00Z");
    private static final OffsetDateTime PAUSE = OffsetDateTime.parse("2026-09-04T09:00:00Z");
    private static final OffsetDateTime RESUME = OffsetDateTime.parse("2026-09-04T10:00:00Z");
    private static final OffsetDateTime FINISH = OffsetDateTime.parse("2026-09-04T11:00:00Z");

    private InMemoryDispatchRepository dispatchRepository;
    private InMemoryOperationExecutionRepository operationRepository;
    private DispatchApplicationServiceImpl dispatchService;
    private OperationExecutionApplicationServiceImpl operationService;
    private AtomicBoolean released;

    @BeforeEach
    void setUp() {
        bind(TENANT_A);
        released = new AtomicBoolean(true);
        dispatchRepository = new InMemoryDispatchRepository();
        operationRepository = new InMemoryOperationExecutionRepository();
        dispatchService = new DispatchApplicationServiceImpl(dispatchRepository,
                (tenant, workOrder) -> TENANT_A.equals(tenant) && WORK_ORDER.equals(workOrder)
                        && released.get());
        operationService = new OperationExecutionApplicationServiceImpl(operationRepository,
                dispatchRepository,
                (tenant, workOrder) -> TENANT_A.equals(tenant) && WORK_ORDER.equals(workOrder)
                        && released.get());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        RequestContextHolder.clear();
    }

    /** 验证派工草稿到发布、处理、完成的状态路径和幂等重放。 */
    @Test
    void dispatchFollowsDraftReleasedProcessingCompleted() {
        DispatchOrder draft = dispatchService.create(
                new DispatchCreateRequest(WORK_ORDER, OPERATION, DEVICE), "dispatch-create");
        assertEquals(DispatchStatus.Draft, draft.status());
        DispatchOrder releasedOrder = dispatchService.release(draft.id(), "dispatch-release");
        assertEquals(DispatchStatus.Released, releasedOrder.status());
        assertEquals(releasedOrder, dispatchService.release(draft.id(), "dispatch-release"));
        DispatchOrder processing = dispatchService.startProcessing(draft.id(), "dispatch-start");
        assertEquals(DispatchStatus.Processing, processing.status());
        assertEquals(DispatchStatus.Completed,
                dispatchService.complete(draft.id(), "dispatch-complete").status());
    }

    /** 工单未 Released 时禁止创建派工和开始工序，且业务码稳定。 */
    @Test
    void unreleasedWorkOrderCannotBeDispatchedOrStarted() {
        released.set(false);
        DispatchException dispatchError = assertThrows(DispatchException.class,
                () -> dispatchService.create(new DispatchCreateRequest(WORK_ORDER, OPERATION, null),
                        "dispatch-blocked"));
        assertEquals("MES_DISPATCH_002", dispatchError.getBusinessCode());

        released.set(true);
        DispatchOrder dispatch = dispatchService.create(
                new DispatchCreateRequest(WORK_ORDER, OPERATION, null), "dispatch-for-start");
        dispatchService.release(dispatch.id(), "release-for-start");
        OperationExecution execution = operationService.create(new OperationExecutionCreateRequest(
                dispatch.id(), WORK_ORDER, OPERATION, null), "execution-create");
        released.set(false);
        OperationExecutionException operationError = assertThrows(OperationExecutionException.class,
                () -> operationService.start(execution.id(), START, "execution-start-blocked"));
        assertEquals("MES_OPERATION_004", operationError.getBusinessCode());
    }

    /** 验证工序状态链、暂停原因、恢复/完成时间和操作人均留在事件时间线。 */
    @Test
    void operationExecutionKeepsIndependentTimeline() {
        OperationExecution execution = createExecution(DEVICE);
        OperationExecution running = operationService.start(execution.id(), START, "op-start");
        OperationExecution paused = operationService.pause(execution.id(), "设备换刀", PAUSE, "op-pause");
        OperationExecution resumed = operationService.resume(execution.id(), RESUME, "op-resume");
        OperationExecution completed = operationService.complete(execution.id(), FINISH, "op-complete");

        assertEquals(OperationExecutionStatus.Running, running.status());
        assertEquals(OperationExecutionStatus.Paused, paused.status());
        assertEquals(OperationExecutionStatus.Running, resumed.status());
        assertEquals(OperationExecutionStatus.Completed, completed.status());
        assertEquals(4, completed.events().size());
        assertEquals("设备换刀", completed.events().get(1).reason());
        assertEquals(USER, completed.events().get(0).operatorId());
        assertEquals(FINISH, completed.events().get(3).occurredAt());
        assertEquals(OperationExecutionEventType.COMPLETED, completed.events().get(3).type());

        OperationExecutionException duplicate = assertThrows(OperationExecutionException.class,
                () -> operationService.complete(execution.id(), FINISH, "op-complete-again"));
        assertEquals("MES_OPERATION_003", duplicate.getBusinessCode());
    }

    /** 仅提供派工单时，工单、工序和设备安排从派工事实继承。 */
    @Test
    void operationExecutionInheritsDispatchContext() {
        DispatchOrder dispatch = dispatchService.create(
                new DispatchCreateRequest(WORK_ORDER, OPERATION, USER, java.math.BigDecimal.TEN, DEVICE),
                "dispatch-inherit");
        dispatchService.release(dispatch.id(), "release-inherit");

        OperationExecution execution = operationService.create(
                new OperationExecutionCreateRequest(dispatch.id()), "execution-inherit");

        assertEquals(WORK_ORDER, execution.workOrderId());
        assertEquals(OPERATION, execution.operationId());
        assertEquals(DEVICE, execution.deviceId());
    }

    /** 同一设备活动执行不能重叠，保证上下文端口不会返回歧义结果。 */
    @Test
    void deviceActivityIsUniqueAndTenantScoped() {
        OperationExecution first = createExecution(DEVICE);
        operationService.start(first.id(), START, "unique-start-1");
        OperationExecution second = createExecution(DEVICE);
        OperationExecutionException conflict = assertThrows(OperationExecutionException.class,
                () -> operationService.start(second.id(), START, "unique-start-2"));
        assertEquals("MES_OPERATION_006", conflict.getBusinessCode());

        InMemoryProductionContextQuery query = new InMemoryProductionContextQuery(operationRepository);
        ProductionContext context = query.findActive(TENANT_A, DEVICE,
                OffsetDateTime.parse("2026-09-04T08:30:00Z")).orElseThrow();
        assertEquals(WORK_ORDER, context.workOrderId());
        assertEquals(first.id(), context.operationExecutionId());
        assertTrue(query.findActive(TENANT_B, DEVICE,
                OffsetDateTime.parse("2026-09-04T08:30:00Z")).isEmpty());
        assertTrue(query.findActive(TENANT_A, DEVICE,
                OffsetDateTime.parse("2026-09-04T07:30:00Z")).isEmpty());
    }

    /** 暂停是活动上下文，完成后告警时刻不再返回该执行。 */
    @Test
    void contextQueryUsesHistoricalTimeline() {
        OperationExecution execution = createExecution(DEVICE);
        operationService.start(execution.id(), START, "history-start");
        operationService.pause(execution.id(), "缺料", PAUSE, "history-pause");
        InMemoryProductionContextQuery query = new InMemoryProductionContextQuery(operationRepository);

        assertTrue(query.findActive(TENANT_A, DEVICE,
                OffsetDateTime.parse("2026-09-04T09:30:00Z")).isPresent());
        operationService.resume(execution.id(), RESUME, "history-resume");
        operationService.complete(execution.id(), FINISH, "history-finish");
        assertTrue(query.findActive(TENANT_A, DEVICE,
                OffsetDateTime.parse("2026-09-04T11:00:00Z")).isEmpty());
    }

    private OperationExecution createExecution(UUID deviceId) {
        DispatchOrder dispatch = dispatchService.create(
                new DispatchCreateRequest(WORK_ORDER, OPERATION, deviceId),
                "dispatch-" + UUID.randomUUID());
        dispatchService.release(dispatch.id(), "release-" + UUID.randomUUID());
        return operationService.create(new OperationExecutionCreateRequest(dispatch.id(), WORK_ORDER,
                OPERATION, deviceId), "execution-" + UUID.randomUUID());
    }

    private void bind(UUID tenantId) {
        TenantContextHolder.setTenantId(tenantId);
        RequestContextHolder.getContext().setUserId(USER);
        RequestContextHolder.getContext().setJti("task15-jti");
        RequestContextHolder.getContext().setRequestId("task15-request");
    }
}
