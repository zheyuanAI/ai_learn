package com.ailearn.platform.iot.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import com.ailearn.platform.iot.alarm.application.AlarmApplicationServiceImpl;
import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.domain.port.AlarmRuleFactsPort;
import com.ailearn.platform.iot.alarm.exception.AlarmException;
import com.ailearn.platform.iot.alarm.infrastructure.InMemoryAlarmRepository;
import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationService;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.telemetry.application.TelemetryCredentialContext;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryMetric;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AlarmApplicationServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID RULE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    private InMemoryAlarmRepository repository;
    private AlarmRuleFactsPort ruleFactsPort;
    private AlarmApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        ruleFactsPort = Mockito.mock(AlarmRuleFactsPort.class);
        when(ruleFactsPort.findActiveRules(TENANT_ID, DEVICE_ID)).thenReturn(List.of(rule()));
        repository = new InMemoryAlarmRepository();
        service = new AlarmApplicationServiceImpl(repository, ruleFactsPort,
                new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(),
                        new ObjectMapper().findAndRegisterModules()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void thresholdHysteresisAndAckedRecoveryFollowLifecycle() {
        service.onTelemetryAccepted(command("m-1", BASE_TIME, "11"), null);
        AlarmFact triggered = repository.findActive(TENANT_ID, DEVICE_ID, RULE_ID).orElseThrow();
        assertEquals(AlarmStatus.Triggered, triggered.status());

        service.onTelemetryAccepted(command("m-2", BASE_TIME.plusMinutes(1), "12"), null);
        assertEquals(1L, repository.count(TENANT_ID, null, null, null, null, null, null));

        var acked = service.ack(triggered.id(), "已通知值班人员", "ack-1");
        assertEquals("Acked", acked.status());
        var recovered = repository.findById(TENANT_ID, triggered.id()).orElseThrow();
        assertEquals(USER_ID, recovered.updatedBy());
        assertEquals(recovered.ackedAt(), recovered.updatedAt());

        service.onTelemetryAccepted(command("m-3", BASE_TIME.plusMinutes(2), "8"), null);
        AlarmFact completed = repository.findById(TENANT_ID, triggered.id()).orElseThrow();
        assertEquals(AlarmStatus.Recovered, completed.status());
        assertEquals(BASE_TIME.plusMinutes(2), completed.recoveredAt());
        assertEquals(recovered.ackedAt(), completed.ackedAt());
    }

    /**
     * 用途：验证超大合法页码不会因偏移溢出而错误返回第一页告警。
     * 入参：已有一条告警、最大 int 页码与最大页大小；出参：空记录页及真实总数。
     */
    @Test
    void largePageDoesNotReturnFirstPageAgain() {
        service.onTelemetryAccepted(command("m-large-page", BASE_TIME, "11"), null);

        var page = service.page(null, null, null, null, null, null, Integer.MAX_VALUE, 100);

        assertEquals(0, page.records().size());
        assertEquals(1, page.total());
    }

    @Test
    void recoveryBeforeAckKeepsRecoveryTimeAndThenAckCompletes() {
        service.onTelemetryAccepted(command("m-4", BASE_TIME, "11"), null);
        UUID alarmId = repository.findActive(TENANT_ID, DEVICE_ID, RULE_ID).orElseThrow().id();

        service.onTelemetryAccepted(command("m-5", BASE_TIME.plusMinutes(3), "8"), null);
        AlarmFact recoveredUnacked = repository.findById(TENANT_ID, alarmId).orElseThrow();
        assertEquals(AlarmStatus.RecoveredUnacked, recoveredUnacked.status());
        assertEquals(BASE_TIME.plusMinutes(3), recoveredUnacked.recoveredAt());

        service.ack(alarmId, "恢复后确认", "ack-2");
        AlarmFact completed = repository.findById(TENANT_ID, alarmId).orElseThrow();
        assertEquals(AlarmStatus.Recovered, completed.status());
        assertEquals(BASE_TIME.plusMinutes(3), completed.recoveredAt());
    }

    @Test
    void sameAckKeyIsIdempotentAndDifferentPayloadConflicts() {
        service.onTelemetryAccepted(command("m-6", BASE_TIME, "11"), null);
        UUID alarmId = repository.findActive(TENANT_ID, DEVICE_ID, RULE_ID).orElseThrow().id();

        var first = service.ack(alarmId, "备注", "ack-3");
        var replay = service.ack(alarmId, "备注", "ack-3");
        assertEquals(first.id(), replay.id());
        assertEquals(first.status(), replay.status());
        assertEquals(first.ackedAt().toInstant(), replay.ackedAt().toInstant());

        assertThrows(RuntimeException.class, () -> service.ack(alarmId, "其他备注", "ack-3"));
    }

    @Test
    void crossTenantAlarmIsNotReadable() {
        service.onTelemetryAccepted(command("m-7", BASE_TIME, "11"), null);
        UUID alarmId = repository.findActive(TENANT_ID, DEVICE_ID, RULE_ID).orElseThrow().id();
        RequestContextHolder.getContext().setTenantId(OTHER_TENANT_ID);

        assertThrows(RuntimeException.class, () -> service.detail(alarmId));
        assertEquals(0, service.page(null, null, null, null, null, null, 1, 20).total());
    }

    @Test
    void repeatedAckAfterCompletionIsRejectedWhenUsingNewKey() {
        service.onTelemetryAccepted(command("m-8", BASE_TIME, "11"), null);
        UUID alarmId = repository.findActive(TENANT_ID, DEVICE_ID, RULE_ID).orElseThrow().id();
        service.ack(alarmId, "一次确认", "ack-4");
        assertThrows(AlarmException.class, () -> service.ack(alarmId, "二次确认", "ack-5"));
    }

    /** 修改用途：本地入队失败必须传播给摄取事务，防止返回成功后遗留无法补链的告警。 */
    @Test
    void enqueueFailureIsPropagatedForMessageRedelivery() {
        AlarmContextLinkApplicationService contextLink = Mockito.mock(AlarmContextLinkApplicationService.class);
        ServiceUnavailableException unavailable = new ServiceUnavailableException("本地任务数据库不可用");
        doThrow(unavailable).when(contextLink).enqueue(eq(TENANT_ID), any(UUID.class));
        service = new AlarmApplicationServiceImpl(repository, ruleFactsPort,
                new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(),
                        new ObjectMapper().findAndRegisterModules()), null, contextLink);

        assertSame(unavailable, assertThrows(ServiceUnavailableException.class,
                () -> service.onTelemetryAccepted(command("m-9", BASE_TIME, "11"), null)));
    }

    /** 遥测触发告警时只允许本地入队，不能在事实保存链上同步执行 Core 补链。 */
    @Test
    void triggeredAlarmDoesNotLinkContextImmediately() {
        AlarmContextLinkApplicationService contextLink = Mockito.mock(AlarmContextLinkApplicationService.class);
        service = new AlarmApplicationServiceImpl(repository, ruleFactsPort,
                new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(),
                        new ObjectMapper().findAndRegisterModules()), null, contextLink);

        service.onTelemetryAccepted(command("m-deferred", BASE_TIME, "11"), null);

        UUID alarmId = repository.findActive(TENANT_ID, DEVICE_ID, RULE_ID).orElseThrow().id();
        verify(contextLink).enqueue(TENANT_ID, alarmId);
        verify(contextLink, never()).link(any(UUID.class), any(UUID.class));
    }

    private AlarmRule rule() {
        return new AlarmRule(RULE_ID, TENANT_ID, "temperature-high", UUID.randomUUID(), null,
                "temperature", "GT", new BigDecimal("10"), new BigDecimal("8"), "High", "ACTIVE",
                USER_ID, BASE_TIME);
    }

    private TelemetryIngestionCommand command(String messageId, OffsetDateTime timestamp, String value) {
        return new TelemetryIngestionCommand(new TelemetryCredentialContext(TENANT_ID, DEVICE_ID, "cred-1"),
                DEVICE_ID, "M-001", timestamp, timestamp.plusSeconds(1), messageId, null,
                List.of(new TelemetryMetric("temperature", new BigDecimal(value), "C")), "payload-hash");
    }
}
