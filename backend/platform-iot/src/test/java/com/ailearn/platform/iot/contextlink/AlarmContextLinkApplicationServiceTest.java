package com.ailearn.platform.iot.contextlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationServiceImpl;
import com.ailearn.platform.iot.contextlink.domain.AlarmContextCandidate;
import com.ailearn.platform.iot.contextlink.domain.ContextLinkResult;
import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import com.ailearn.platform.iot.contextlink.domain.port.ProductionContextQueryPort;
import com.ailearn.platform.iot.contextlink.infrastructure.InMemoryAlarmContextLinkRepository;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

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
