package com.ailearn.platform.iot.contextlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationServiceImpl;
import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkRetryScheduler;
import com.ailearn.platform.iot.contextlink.domain.AlarmContextCandidate;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkResult;
import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.ailearn.platform.iot.contextlink.domain.port.ProductionContextQueryPort;
import com.ailearn.platform.iot.contextlink.domain.port.AlarmContextLinkRepository;
import com.ailearn.platform.iot.contextlink.infrastructure.InMemoryAlarmContextLinkRepository;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AlarmContextLinkApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID ALARM_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID EXECUTION_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION_ID = UUID.fromString("f0000000-0000-0000-0000-000000000003");
    private static final OffsetDateTime ALARM_TIME = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T10:01:00Z"), ZoneOffset.UTC);

    /** 设备事实保存阶段仅持久化 Pending 任务，不调用任何 Core 查询。 */
    @Test
    void enqueuePersistsPendingTaskWithoutQueryingCore() {
        InMemoryAlarmContextLinkRepository repository = repository();
        ProductionContextQueryPort query = Mockito.mock(ProductionContextQueryPort.class);
        AlarmContextLinkApplicationServiceImpl service = service(repository, query);

        service.enqueue(TENANT_ID, ALARM_ID);

        assertEquals("Pending", repository.task(ALARM_ID).orElseThrow().status());
        assertEquals("Pending", repository.alarm(ALARM_ID).orElseThrow().contextStatus());
        verifyNoInteractions(query);
    }

    /** 用途：验证跨租户入队不会生成任务或查询 Core，无业务出入参。 */
    @Test
    void enqueueHidesOtherTenantAlarm() {
        InMemoryAlarmContextLinkRepository repository = repository();
        ProductionContextQueryPort query = Mockito.mock(ProductionContextQueryPort.class);

        service(repository, query).enqueue(OTHER_TENANT_ID, ALARM_ID);

        assertNull(repository.task(ALARM_ID).orElse(null));
        verifyNoInteractions(query);
    }

    /** 本地入队失败必须保留原异常，以便上层事务回滚并让消息重投。 */
    @Test
    void enqueueDatabaseFailureIsPropagatedWithoutCoreQuery() {
        AlarmContextLinkRepository repository = Mockito.mock(AlarmContextLinkRepository.class);
        ProductionContextQueryPort query = Mockito.mock(ProductionContextQueryPort.class);
        when(repository.findAlarm(TENANT_ID, ALARM_ID))
                .thenReturn(repository().alarm(ALARM_ID));
        ServiceUnavailableException unavailable = new ServiceUnavailableException("任务数据库不可用");
        doThrow(unavailable).when(repository).enqueue(eq(TENANT_ID), eq(ALARM_ID), any(OffsetDateTime.class));
        AlarmContextLinkApplicationServiceImpl service = new AlarmContextLinkApplicationServiceImpl(
                repository, query, CLOCK);

        assertSame(unavailable, assertThrows(ServiceUnavailableException.class,
                () -> service.enqueue(TENANT_ID, ALARM_ID)));
        verifyNoInteractions(query);
    }

    /**
     * 用途：验证后台调度在 Core 故障后保留任务，并在恢复时补链；无业务出入参。
     * 流程：入队不查 Core，首轮调度进入 Retry，固定时间推进到退避截止后成功关联并完成任务。
     */
    @Test
    void schedulerLinksQueuedAlarmAfterCoreRecovers() {
        InMemoryAlarmContextLinkRepository repository = repository();
        AtomicBoolean coreAvailable = new AtomicBoolean();
        AtomicInteger queries = new AtomicInteger();
        ProductionContextQueryPort query = (tenant, device, time) -> {
            queries.incrementAndGet();
            if (!coreAvailable.get()) {
                throw new ServiceUnavailableException("Core unavailable");
            }
            return Optional.of(context());
        };
        AlarmContextLinkApplicationServiceImpl service = service(repository, query);
        service.enqueue(TENANT_ID, ALARM_ID);
        assertEquals(0, queries.get());

        new AlarmContextLinkRetryScheduler(repository, service, CLOCK).retryDueTasks();

        assertEquals(1, queries.get());
        assertEquals("Retry", repository.task(ALARM_ID).orElseThrow().status());
        assertEquals("Pending", repository.alarm(ALARM_ID).orElseThrow().contextStatus());
        coreAvailable.set(true);
        Clock recoveredClock = Clock.offset(CLOCK, Duration.ofSeconds(5));
        AlarmContextLinkApplicationServiceImpl recoveredService = new AlarmContextLinkApplicationServiceImpl(
                repository, query, recoveredClock);
        new AlarmContextLinkRetryScheduler(repository, recoveredService, recoveredClock).retryDueTasks();

        assertEquals(2, queries.get());
        assertEquals("Completed", repository.task(ALARM_ID).orElseThrow().status());
        AlarmContextCandidate linked = repository.alarm(ALARM_ID).orElseThrow();
        assertEquals("Linked", linked.contextStatus());
        assertEquals(EXECUTION_ID, linked.operationExecutionId());
        assertEquals(ALARM_TIME, linked.alarmTime());
    }

    @Test
    void uniqueContextIsWrittenWithoutChangingAlarmTime() {
        InMemoryAlarmContextLinkRepository repository = repository();
        ProductionContextQueryPort query = (tenant, device, time) -> Optional.of(context());
        AlarmContextLinkApplicationServiceImpl service = service(repository, query);

        ContextLinkResult result = service.link(TENANT_ID, ALARM_ID);

        assertEquals(ContextLinkResult.Status.LINKED, result.status());
        AlarmContextCandidate linked = repository.alarm(ALARM_ID).orElseThrow();
        assertEquals("Automatic", linked.contextSource());
        assertEquals("Linked", linked.contextStatus());
        assertEquals(EXECUTION_ID, linked.operationExecutionId());
        assertEquals(ALARM_TIME, linked.alarmTime());
        assertEquals("Completed", repository.task(ALARM_ID).orElseThrow().status());
    }

    @Test
    void noMatchKeepsPendingAndSchedulesRetryWithoutIdentifiers() {
        InMemoryAlarmContextLinkRepository repository = repository();
        AlarmContextLinkApplicationServiceImpl service = service(repository, (tenant, device, time) -> Optional.empty());

        ContextLinkResult result = service.link(TENANT_ID, ALARM_ID);

        assertEquals(ContextLinkResult.Status.RETRY_SCHEDULED, result.status());
        AlarmContextCandidate unchanged = repository.alarm(ALARM_ID).orElseThrow();
        assertEquals("Pending", unchanged.contextStatus());
        assertNull(unchanged.operationExecutionId());
        assertNull(unchanged.workOrderId());
        assertEquals(1, repository.task(ALARM_ID).orElseThrow().retryCount());
    }

    @Test
    void crossTenantOrFutureContextIsRejectedAndCanSucceedOnRetry() {
        InMemoryAlarmContextLinkRepository repository = repository();
        AtomicInteger calls = new AtomicInteger();
        ProductionContextQueryPort query = (tenant, device, time) -> {
            if (calls.getAndIncrement() == 0) {
                return Optional.of(new ProductionContextView(OTHER_TENANT_ID, DEVICE_ID, WORK_ORDER_ID,
                        EXECUTION_ID, OPERATION_ID, ALARM_TIME.minusMinutes(1), ALARM_TIME));
            }
            return Optional.of(context());
        };
        AlarmContextLinkApplicationServiceImpl service = service(repository, query);

        ContextLinkResult first = service.link(TENANT_ID, ALARM_ID);
        assertEquals(ContextLinkResult.Status.RETRY_SCHEDULED, first.status());
        assertEquals("Pending", repository.alarm(ALARM_ID).orElseThrow().contextStatus());

        repository.enqueue(TENANT_ID, ALARM_ID, CLOCK.instant().atOffset(ZoneOffset.UTC));
        ContextLinkResult second = service.link(TENANT_ID, ALARM_ID);
        assertEquals(ContextLinkResult.Status.LINKED, second.status());
        assertEquals("Linked", repository.alarm(ALARM_ID).orElseThrow().contextStatus());
    }

    @Test
    void tenantBoundaryHidesAlarm() {
        InMemoryAlarmContextLinkRepository repository = repository();
        AlarmContextLinkApplicationServiceImpl service = service(repository, (tenant, device, time) -> Optional.of(context()));

        ContextLinkResult result = service.link(OTHER_TENANT_ID, ALARM_ID);

        assertEquals(ContextLinkResult.Status.NOT_FOUND, result.status());
    }

    @Test
    void manualLinkRequiresBothBusinessIdentifiers() {
        RequestContextHolder.getContext().setUserId(UUID.randomUUID());
        try {
            AlarmContextLinkApplicationServiceImpl service = service(repository(),
                    (tenant, device, time) -> Optional.empty());

            IotException exception = org.junit.jupiter.api.Assertions.assertThrows(IotException.class,
                    () -> service.linkManually(TENANT_ID, ALARM_ID, EXECUTION_ID, null, "manual-partial"));

            assertEquals("IOT_CTX_001", exception.getBusinessCode());
        } finally {
            RequestContextHolder.clear();
        }
    }

    private AlarmContextLinkApplicationServiceImpl service(InMemoryAlarmContextLinkRepository repository,
                                                            ProductionContextQueryPort query) {
        return new AlarmContextLinkApplicationServiceImpl(repository, query, CLOCK);
    }

    private InMemoryAlarmContextLinkRepository repository() {
        InMemoryAlarmContextLinkRepository repository = new InMemoryAlarmContextLinkRepository();
        repository.putAlarm(new AlarmContextCandidate(ALARM_ID, TENANT_ID, DEVICE_ID, ALARM_TIME,
                null, "Pending", null, null));
        return repository;
    }

    private ProductionContextView context() {
        return new ProductionContextView(TENANT_ID, DEVICE_ID, WORK_ORDER_ID, EXECUTION_ID, OPERATION_ID,
                ALARM_TIME.minusMinutes(5), ALARM_TIME.minusSeconds(1));
    }
}
