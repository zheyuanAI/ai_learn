package com.ailearn.platform.iot.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import com.ailearn.platform.iot.telemetry.application.TelemetryCredentialContext;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionServiceImpl;
import com.ailearn.platform.iot.telemetry.application.TelemetryMetric;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryAlarmPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryFactPort;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.ailearn.platform.iot.telemetry.infrastructure.InMemoryTelemetryStore;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 通过锁步交错验证设备隔离、事务完成边界与闲置锁回收；内存端口不用于证明 PostgreSQL 回滚。 */
class TelemetryDeviceConcurrencyTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000002");
    private static final UUID PROFILE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-09-04T10:00:00Z");

    private DeviceRepository deviceRepository;
    private DeviceProfileRepository profileRepository;
    private InMemoryTelemetryStore store;

    /** 用途：装配支持任意租户/设备的只读夹具；无入参出参，事实由每次测试的内存端口保存。 */
    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        profileRepository = mock(DeviceProfileRepository.class);
        store = new InMemoryTelemetryStore();
        when(deviceRepository.findDeviceById(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(device(invocation.getArgument(0), invocation.getArgument(1))));
        when(profileRepository.findProfileById(any(UUID.class), any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(profile(invocation.getArgument(0))));
    }

    /** 第一台设备暂停事实追加时，另一台设备必须能够完成摄取，不依赖睡眠排序。 */
    @Test
    void differentDevicesCanCompleteWhileFirstDeviceIsBlocked() throws Exception {
        assertIndependentScopeCanProgress(TENANT_ID, OTHER_DEVICE_ID);
    }

    /** 相同设备 UUID 位于不同租户时也必须独立执行，不能跨租户共用设备锁。 */
    @Test
    void sameDeviceIdInDifferentTenantsCanProgressIndependently() throws Exception {
        assertIndependentScopeCanProgress(OTHER_TENANT_ID, DEVICE_ID);
    }

    /**
     * 用途：验证方法返回后同设备仍阻塞到真实 Spring 事务完成回调；无业务入参出参。
     * 流程：暂停事务提交，启动同消息重投，确认尚未放行；完成提交后只重放一组事实。
     */
    @Test
    void duplicateWaitsUntilEarlierTransactionCompletes() throws Exception {
        CountDownLatch commitEntered = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch duplicateStarted = new CountDownLatch(1);
        AtomicInteger notifications = new AtomicInteger();
        TelemetryIngestionServiceImpl service = service(store, (message, status) -> notifications.incrementAndGet());
        TransactionTemplate transaction = new TransactionTemplate(new CompletionGateTransactionManager(
                commitEntered, allowCommit));
        TelemetryIngestionCommand message = command(TENANT_ID, DEVICE_ID, "same", BASE_TIME);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<TelemetryIngestionResult> first = workers.submit(() -> transaction.execute(
                    status -> service.ingest(message)));
            assertTrue(commitEntered.await(5, TimeUnit.SECONDS), "首条消息未进入提交阶段");
            Future<TelemetryIngestionResult> duplicate = workers.submit(() -> {
                duplicateStarted.countDown();
                return service.ingest(message);
            });
            assertTrue(duplicateStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> duplicate.get(150, TimeUnit.MILLISECONDS),
                    "同设备消息不能在前一事务提交前进入摄取链");

            allowCommit.countDown();
            TelemetryIngestionResult accepted = first.get(5, TimeUnit.SECONDS);
            TelemetryIngestionResult replay = duplicate.get(5, TimeUnit.SECONDS);
            assertFalse(accepted.duplicate());
            assertTrue(replay.duplicate());
            assertEquals(accepted.telemetryIds(), replay.telemetryIds());
            assertEquals(1, store.telemetryCount(TENANT_ID, DEVICE_ID));
            assertEquals(1, notifications.get());
            assertLocksReclaimed(service);
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
    }

    /** 等待前一事务完成的迟到消息仍追加事实，但不得倒退状态或重新通知告警。 */
    @Test
    void delayedMessageAfterCommitCannotRegressStateOrAlarm() throws Exception {
        CountDownLatch commitEntered = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        AtomicInteger notifications = new AtomicInteger();
        TelemetryIngestionServiceImpl service = service(store, (message, status) -> notifications.incrementAndGet());
        TransactionTemplate transaction = new TransactionTemplate(new CompletionGateTransactionManager(
                commitEntered, allowCommit));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<TelemetryIngestionResult> newer = workers.submit(() -> transaction.execute(status ->
                    service.ingest(command(TENANT_ID, DEVICE_ID, "newer", BASE_TIME.plusMinutes(2)))));
            assertTrue(commitEntered.await(5, TimeUnit.SECONDS));
            Future<TelemetryIngestionResult> older = workers.submit(() ->
                    service.ingest(command(TENANT_ID, DEVICE_ID, "older", BASE_TIME)));
            allowCommit.countDown();
            newer.get(5, TimeUnit.SECONDS);
            TelemetryIngestionResult delayed = older.get(5, TimeUnit.SECONDS);

            assertEquals(BASE_TIME.plusMinutes(2), delayed.status().sourceTimestamp());
            assertEquals(DEVICE_ID + "|message_id|newer", delayed.status().lastMessageKey());
            assertEquals(2, store.telemetryCount(TENANT_ID, DEVICE_ID));
            assertEquals(1, notifications.get());
            assertLocksReclaimed(service);
        } finally {
            allowCommit.countDown();
            workers.shutdownNow();
        }
    }

    /** 校验阶段依赖失败触发 Spring 回滚回调后，另一线程可安全重投同一消息。 */
    @Test
    void rollbackReleasesDeviceForSameMessageRedelivery() throws Exception {
        ServiceUnavailableException unavailable = new ServiceUnavailableException("设备模型数据库暂不可用");
        when(profileRepository.findProfileById(TENANT_ID, PROFILE_ID))
                .thenThrow(unavailable).thenReturn(Optional.of(profile(TENANT_ID)));
        TelemetryIngestionServiceImpl service = service(store, (message, status) -> { });
        CompletionGateTransactionManager manager = new CompletionGateTransactionManager(null, null);
        TransactionTemplate transaction = new TransactionTemplate(manager);
        TelemetryIngestionCommand message = command(TENANT_ID, DEVICE_ID, "retry", BASE_TIME);

        assertSame(unavailable, assertThrows(ServiceUnavailableException.class,
                () -> transaction.execute(status -> service.ingest(message))));
        assertEquals(1, manager.rollbacks.get());
        assertEquals(0, store.telemetryCount(TENANT_ID, DEVICE_ID));
        assertLocksReclaimed(service);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            TelemetryIngestionResult retry = worker.submit(() -> transaction.execute(
                    status -> service.ingest(message))).get(5, TimeUnit.SECONDS);
            assertTrue(retry.accepted());
            assertFalse(retry.duplicate());
            assertEquals(1, store.telemetryCount(TENANT_ID, DEVICE_ID));
            assertLocksReclaimed(service);
        } finally {
            worker.shutdownNow();
        }
    }

    /** 无事务调用成功和非法消息失败后都回收锁，不为历史设备无限累积表项。 */
    @Test
    void completedAndRejectedDevicesDoNotAccumulateLocks() {
        TelemetryIngestionServiceImpl service = service(store, (message, status) -> { });
        for (int index = 0; index < 20; index++) {
            UUID deviceId = UUID.randomUUID();
            TelemetryIngestionCommand accepted = command(TENANT_ID, deviceId, "valid", BASE_TIME);
            service.ingest(accepted);
            TelemetryIngestionCommand rejected = new TelemetryIngestionCommand(accepted.credentialContext(),
                    deviceId, accepted.deviceCode(), BASE_TIME, BASE_TIME, "invalid", null, List.of(), "a".repeat(64));
            assertThrows(TelemetryException.class, () -> service.ingest(rejected));
            assertLocksReclaimed(service);
        }
    }

    /**
     * 用途：固定第一范围停在事实追加，验证另一范围可完成；入参为第二范围，无出参。
     * 流程：闩锁确认首条已持锁，再等待另一条独立完成，最后放行并检查锁回收。
     */
    private void assertIndependentScopeCanProgress(UUID secondTenant, UUID secondDevice) throws Exception {
        CountDownLatch firstAppendEntered = new CountDownLatch(1);
        CountDownLatch allowFirstAppend = new CountDownLatch(1);
        TelemetryFactPort facts = messageFacts -> {
            var firstFact = messageFacts.getFirst();
            if (TENANT_ID.equals(firstFact.tenantId()) && DEVICE_ID.equals(firstFact.deviceId())) {
                firstAppendEntered.countDown();
                await(allowFirstAppend);
            }
            return store.append(messageFacts);
        };
        TelemetryIngestionServiceImpl service = service(facts, (message, status) -> { });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<TelemetryIngestionResult> first = workers.submit(() ->
                    service.ingest(command(TENANT_ID, DEVICE_ID, "first", BASE_TIME)));
            assertTrue(firstAppendEntered.await(5, TimeUnit.SECONDS), "首条消息未进入事实追加");
            Future<TelemetryIngestionResult> second = workers.submit(() ->
                    service.ingest(command(secondTenant, secondDevice, "second", BASE_TIME)));
            assertTrue(second.get(5, TimeUnit.SECONDS).accepted(), "另一设备应在首条被阻塞期间完成");
            assertFalse(first.isDone());
            allowFirstAppend.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertLocksReclaimed(service);
        } finally {
            allowFirstAppend.countDown();
            workers.shutdownNow();
        }
    }

    /** 用途：装配被测服务；入参为事实端口和告警端口，出参为共享同一内存去重/状态端口的实例。 */
    private TelemetryIngestionServiceImpl service(TelemetryFactPort facts, TelemetryAlarmPort alarms) {
        return new TelemetryIngestionServiceImpl(deviceRepository, profileRepository, store, facts, store, alarms);
    }

    /** 用途：断言闲置设备锁被全部回收；入参为服务，无出参，读取内部表仅验证资源生命周期。 */
    private void assertLocksReclaimed(TelemetryIngestionServiceImpl service) {
        Map<?, ?> locks = (Map<?, ?>) ReflectionTestUtils.getField(service, "deviceLocks");
        assertEquals(0, locks.size(), "完成/失败后不能遗留设备锁表项");
    }

    /** 用途：生成可信设备消息；入参为范围、消息键和设备时间，出参为合法单指标命令。 */
    private TelemetryIngestionCommand command(UUID tenantId, UUID deviceId, String key, OffsetDateTime timestamp) {
        return new TelemetryIngestionCommand(new TelemetryCredentialContext(tenantId, deviceId, "cred-1"),
                deviceId, "M-001", timestamp, timestamp.plusSeconds(1), key, null,
                List.of(new TelemetryMetric("temperature", "42.5", "C")), "a".repeat(64));
    }

    /** 用途：生成指定租户设备夹具；入参为范围，出参为启用的 MQTT 设备。 */
    private Device device(UUID tenantId, UUID deviceId) {
        return new Device(deviceId, tenantId, "M-001", "机台", PROFILE_ID, "MQTT", DeviceLifecycleStatus.Active,
                null, null, null, null, BASE_TIME, null, BASE_TIME);
    }

    /** 用途：生成单指标模型夹具；入参为租户，出参为允许 temperature 的启用模型。 */
    private DeviceProfile profile(UUID tenantId) {
        return new DeviceProfile(PROFILE_ID, tenantId, "machine", "机台", "ACTIVE", 60,
                List.of(new DeviceProfile.MetricDefinition("temperature", "温度", MetricValueType.NUMBER, "C", true)),
                null, BASE_TIME, null, BASE_TIME);
    }

    /** 用途：在测试工作线程内有界等待闩锁；入参为闩锁，无出参，超时/中断转换为明确测试失败。 */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "测试交错未按时释放");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("测试线程被中断", exception);
        }
    }

    /** 复用 Spring 事务完成机制，只暂停提交阶段；不模拟数据库的持久化/回滚语义。 */
    private static final class CompletionGateTransactionManager extends AbstractPlatformTransactionManager {
        private final CountDownLatch commitEntered;
        private final CountDownLatch allowCommit;
        private final AtomicInteger rollbacks = new AtomicInteger();

        /** 用途：设置可选提交闩锁；入参为进入/释放闩锁，构造无数据库测试事务管理器。 */
        private CompletionGateTransactionManager(CountDownLatch commitEntered, CountDownLatch allowCommit) {
            this.commitEntered = commitEntered;
            this.allowCommit = allowCommit;
        }

        /** 为每次测试调用提供独立事务对象，不共享业务或 JDBC 状态。 */
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        /** 无数据库资源需要开启；实际同步标记和回调由 Spring 基类管理。 */
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        /** 提交阶段停在闩锁，验证应用方法已经返回时设备锁仍然持有。 */
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (commitEntered != null) {
                commitEntered.countDown();
                await(allowCommit);
            }
        }

        /** 记录测试回滚次数，后续设备锁释放由 Spring afterCompletion 回调负责。 */
        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks.incrementAndGet();
        }
    }
}
