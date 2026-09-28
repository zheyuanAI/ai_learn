package com.ailearn.platform.iot.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.ailearn.platform.iot.alarm.application.AlarmApplicationServiceImpl;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.infrastructure.DeviceProfileAlarmRuleFactsAdapter;
import com.ailearn.platform.iot.alarm.infrastructure.PostgresAlarmRepository;
import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationService;
import com.ailearn.platform.iot.contextlink.application.AlarmContextLinkApplicationServiceImpl;
import com.ailearn.platform.iot.contextlink.infrastructure.PostgresAlarmContextLinkRepository;
import com.ailearn.platform.iot.device.application.IotIdempotencyExecutor;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.DeviceLifecycleStatus;
import com.ailearn.platform.iot.device.infrastructure.PostgresIotRepository;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import com.ailearn.platform.iot.profile.domain.MetricValueType;
import com.ailearn.platform.iot.telemetry.application.TelemetryCredentialContext;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionService;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionServiceImpl;
import com.ailearn.platform.iot.telemetry.application.TelemetryMetric;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryAlarmPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryFactPort;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.ailearn.platform.iot.telemetry.infrastructure.PostgresTelemetryStore;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 可选 PostgreSQL 12 摄取并发回归；只操作随机 iot_concurrency_ 数据库，不回退到开发库。
 * 显式设置 iot.test.pg12.jdbc-url/username/password 才执行，每条场景在 finally 中清理其随机数据库。
 */
class IotPostgresConcurrencyTest {
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-09-04T10:00:00Z");
    private static String adminUrl;
    private static String username;
    private static String password;
    private static String databaseUrlPrefix;

    /**
     * 用途：校验显式隔离连接；无业务入参出参。
     * 流程：缺少参数则跳过，拒绝非本机 postgres 管理库及实际 5433 端口，再核实 PostgreSQL 12 主版本。
     */
    @BeforeAll
    static void requireIsolatedPostgres12() throws SQLException {
        adminUrl = System.getProperty("iot.test.pg12.jdbc-url");
        username = System.getProperty("iot.test.pg12.username");
        password = System.getProperty("iot.test.pg12.password");
        Assumptions.assumeTrue(adminUrl != null && !adminUrl.isBlank()
                        && username != null && !username.isBlank() && password != null,
                "未显式提供 IoT 隔离 PostgreSQL 12 参数，跳过真实摄取回归");
        adminUrl = adminUrl.trim();
        assertTrue(adminUrl.startsWith("jdbc:postgresql://"), "测试仅接受 PostgreSQL JDBC URL");
        URI uri = URI.create(adminUrl.substring("jdbc:".length()));
        assertTrue(List.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost()), "测试仅允许本机隔离实例");
        assertTrue(uri.getPort() > 0 && uri.getPort() != 5433, "禁止使用实际开发数据库端口 5433");
        assertEquals("/postgres", uri.getPath(), "只允许 postgres 管理库，禁止 ai_learn 或其他业务库");
        assertNull(uri.getUserInfo(), "连接身份只能从显式系统属性读取");
        assertNull(uri.getRawQuery(), "测试管理连接不接受可能覆盖数据库或会话配置的 URL 参数");
        assertNull(uri.getFragment());
        databaseUrlPrefix = "jdbc:postgresql://" + uri.getRawAuthority() + "/";
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password)) {
            assertEquals(12, connection.getMetaData().getDatabaseMajorVersion(), "隔离实例必须是 PostgreSQL 12");
            assertEquals("postgres", connection.getCatalog(), "实际连接必须仍是管理库");
        }
    }

    /** 两个独立摄取实例同时竞争同消息，数据库只提交一组遥测、状态、告警和补链任务。 */
    @Test
    void concurrentDuplicateAcrossInstancesCreatesOneFactSet() throws Exception {
        withFixture(fixture -> {
            TelemetryIngestionService firstInstance = fixture.service(fixture.store, fixture.alarms, fixture.transactions);
            TelemetryIngestionService secondInstance = fixture.service(fixture.store, fixture.alarms, fixture.transactions);
            TelemetryIngestionCommand message = fixture.command(fixture.firstDevice, "same", BASE_TIME, "42.5", 'a');
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(16);
            try {
                List<Future<TelemetryIngestionResult>> results = new ArrayList<>();
                for (int index = 0; index < 16; index++) {
                    TelemetryIngestionService instance = index % 2 == 0 ? firstInstance : secondInstance;
                    results.add(workers.submit(() -> {
                        await(start);
                        return instance.ingest(message);
                    }));
                }
                start.countDown();
                int originals = 0;
                List<UUID> telemetryIds = null;
                for (Future<TelemetryIngestionResult> result : results) {
                    TelemetryIngestionResult accepted = result.get(20, TimeUnit.SECONDS);
                    assertTrue(accepted.accepted());
                    if (!accepted.duplicate()) {
                        originals++;
                    }
                    if (telemetryIds == null) {
                        telemetryIds = accepted.telemetryIds();
                    }
                    assertEquals(telemetryIds, accepted.telemetryIds());
                }
                assertEquals(1, originals);
                fixture.assertCounts(fixture.firstDevice, 1, 1, 1, 1, 1);
                TelemetryException conflict = assertThrows(TelemetryException.class, () -> firstInstance.ingest(
                        fixture.command(fixture.firstDevice, "same", BASE_TIME, "43.5", 'b')));
                assertEquals("IOT_TLM_003", conflict.getBusinessCode());
                fixture.assertCounts(fixture.firstDevice, 1, 1, 1, 1, 1);
            } finally {
                start.countDown();
                stopWorkers(workers);
            }
        });
    }

    /** 第一台设备持有未提交的真实 JDBC 事务时，第二台设备仍能提交全部 IoT 事实。 */
    @Test
    void otherDeviceCommitsWhileFirstDeviceTransactionIsPaused() throws Exception {
        withFixture(fixture -> {
            CountDownLatch firstAppended = new CountDownLatch(1);
            CountDownLatch allowFirst = new CountDownLatch(1);
            TelemetryFactPort gatedFacts = facts -> {
                List<UUID> ids = fixture.store.append(facts);
                if (fixture.firstDevice.equals(facts.getFirst().deviceId())) {
                    firstAppended.countDown();
                    await(allowFirst);
                }
                return ids;
            };
            TelemetryIngestionService service = fixture.service(gatedFacts, fixture.alarms, fixture.transactions);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<TelemetryIngestionResult> first = workers.submit(() -> service.ingest(
                        fixture.command(fixture.firstDevice, "first", BASE_TIME, "42.5", 'a')));
                assertTrue(firstAppended.await(10, TimeUnit.SECONDS));
                Future<TelemetryIngestionResult> second = workers.submit(() -> service.ingest(
                        fixture.command(fixture.secondDevice, "second", BASE_TIME, "42.5", 'a')));
                assertTrue(second.get(10, TimeUnit.SECONDS).accepted());
                assertFalse(first.isDone());
                fixture.assertCounts(fixture.firstDevice, 0, 0, 0, 0, 0);
                fixture.assertCounts(fixture.secondDevice, 1, 1, 1, 1, 1);
                allowFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                fixture.assertCounts(fixture.firstDevice, 1, 1, 1, 1, 1);
            } finally {
                allowFirst.countDown();
                stopWorkers(workers);
            }
        });
    }

    /** Spring 代理已执行完应用方法但 PostgreSQL 尚未 commit 时，同设备消息必须继续等待。 */
    @Test
    void sameDeviceWaitsForRealJdbcCommitBeforeDuplicateReplay() throws Exception {
        withFixture(fixture -> {
            CountDownLatch commitEntered = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);
            CountDownLatch duplicateStarted = new CountDownLatch(1);
            AtomicInteger deviceValidations = new AtomicInteger();
            DataSourceTransactionManager gatedTransactions = new CommitGateTransactionManager(
                    fixture.dataSource, commitEntered, allowCommit);
            // 只观察真实仓储调用，不替换 SQL；否则重复消息也可能仅被数据库唯一键阻塞而掩盖提前解锁。
            PostgresIotRepository trackedDevices = spy(fixture.repository);
            doAnswer(invocation -> {
                deviceValidations.incrementAndGet();
                return invocation.callRealMethod();
            }).when(trackedDevices).findDeviceById(any(UUID.class), any(UUID.class));
            TelemetryIngestionService service = transactionalProxy(new TelemetryIngestionServiceImpl(
                    trackedDevices, fixture.repository, fixture.store, fixture.store, fixture.store, fixture.alarms),
                    TelemetryIngestionService.class, gatedTransactions);
            TelemetryIngestionCommand message = fixture.command(fixture.firstDevice, "commit", BASE_TIME, "42.5", 'a');
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<TelemetryIngestionResult> first = workers.submit(() -> service.ingest(message));
                assertTrue(commitEntered.await(10, TimeUnit.SECONDS));
                Future<TelemetryIngestionResult> duplicate = workers.submit(() -> {
                    duplicateStarted.countDown();
                    return service.ingest(message);
                });
                assertTrue(duplicateStarted.await(10, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> duplicate.get(150, TimeUnit.MILLISECONDS));
                assertEquals(1, deviceValidations.get(), "提交前第二条同设备消息不能进入数据库校验");
                fixture.assertCounts(fixture.firstDevice, 0, 0, 0, 0, 0);
                allowCommit.countDown();
                TelemetryIngestionResult accepted = first.get(10, TimeUnit.SECONDS);
                TelemetryIngestionResult replay = duplicate.get(10, TimeUnit.SECONDS);
                assertFalse(accepted.duplicate());
                assertTrue(replay.duplicate());
                assertEquals(2, deviceValidations.get());
                assertEquals(accepted.telemetryIds(), replay.telemetryIds());
                fixture.assertCounts(fixture.firstDevice, 1, 1, 1, 1, 1);
            } finally {
                allowCommit.countDown();
                stopWorkers(workers);
            }
        });
    }

    /** 遥测、状态、告警和任务全部写入后抛错，真实事务必须整体回滚，随后同键重投可重新成功。 */
    @Test
    void localFailureRollsBackAllFactsAndAllowsSameKeyRedelivery() throws Exception {
        withFixture(fixture -> {
            AtomicBoolean failFirst = new AtomicBoolean(true);
            ServiceUnavailableException unavailable = new ServiceUnavailableException("模拟本地事务依赖失败");
            TelemetryAlarmPort failingAlarm = (message, status) -> {
                fixture.alarms.onTelemetryAccepted(message, status);
                if (failFirst.compareAndSet(true, false)) {
                    throw unavailable;
                }
            };
            TelemetryIngestionService service = fixture.service(fixture.store, failingAlarm, fixture.transactions);
            TelemetryIngestionCommand message = fixture.command(fixture.firstDevice, "retry", BASE_TIME, "42.5", 'a');
            assertSame(unavailable, assertThrows(ServiceUnavailableException.class, () -> service.ingest(message)));
            fixture.assertCounts(fixture.firstDevice, 0, 0, 0, 0, 0);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                TelemetryIngestionResult accepted = worker.submit(() -> service.ingest(message)).get(10, TimeUnit.SECONDS);
                assertFalse(accepted.duplicate());
                TelemetryIngestionResult replay = service.ingest(message);
                assertTrue(replay.duplicate());
                assertEquals(accepted.telemetryIds(), replay.telemetryIds());
                fixture.assertCounts(fixture.firstDevice, 1, 1, 1, 1, 1);
            } finally {
                stopWorkers(worker);
            }
        });
    }

    /** 旧时间与相同时间的合法消息保存原始事实，但都不能覆盖最新状态或恢复其告警。 */
    @Test
    void delayedAndEqualTimestampFactsCannotRegressStateOrRecoverAlarm() throws Exception {
        withFixture(fixture -> {
            TelemetryIngestionService service = fixture.service(fixture.store, fixture.alarms, fixture.transactions);
            OffsetDateTime newest = BASE_TIME.plusMinutes(2);
            service.ingest(fixture.command(fixture.firstDevice, "newer", newest, "42.5", 'a'));
            service.ingest(fixture.command(fixture.firstDevice, "older", BASE_TIME, "0", 'b'));
            service.ingest(fixture.command(fixture.firstDevice, "equal", newest, "0", 'c'));

            fixture.assertCounts(fixture.firstDevice, 3, 3, 1, 1, 1);
            var status = fixture.store.find(fixture.tenantId, fixture.firstDevice);
            assertEquals(newest, status.sourceTimestamp());
            assertEquals(fixture.firstDevice + "|message_id|newer", status.lastMessageKey());
            var alarm = fixture.alarmRepository.findActive(fixture.tenantId, fixture.firstDevice, fixture.ruleId).orElseThrow();
            assertEquals(AlarmStatus.Triggered, alarm.status());
            assertEquals(newest, alarm.triggeredAt());
            assertNull(alarm.recoveredAt());
        });
    }

    /**
     * 用途：为一条场景创建随机隔离库；入参为场景动作，无出参。
     * 流程：只允许固定随机前缀，运行正式 IoT Flyway 后调用场景；无论成功或异常都清理创建的库。
     */
    private static void withFixture(DatabaseScenario scenario) throws Exception {
        String database = "iot_concurrency_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try {
            requireTestDatabaseName(database);
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE \"" + database + "\"");
            }
            created = true;
            DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrlPrefix + database, username, password);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/iot")
                    .table("iot_flyway_schema_history").baselineOnMigrate(false).load().migrate();
            scenario.run(new Fixture(dataSource));
        } finally {
            if (created) {
                dropTestDatabase(database);
            }
        }
    }

    /** 用途：严格限制创建/删除目标；入参为生成的库名，无出参，拒绝所有非随机测试前缀名称。 */
    private static void requireTestDatabaseName(String database) {
        assertTrue(database.matches("iot_concurrency_[a-f0-9]{32}"), "只允许清理当前随机 IoT 测试数据库");
    }

    /** 用途：清理本场景随机库；入参为已校验库名，无出参，PG12 先关闭其残留连接再执行 DROP。 */
    private static void dropTestDatabase(String database) throws SQLException {
        requireTestDatabaseName(database);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             PreparedStatement terminate = connection.prepareStatement(
                     "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = ? AND pid <> pg_backend_pid()")) {
            terminate.setString(1, database);
            terminate.execute();
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS \"" + database + "\"");
            }
        }
    }

    /** 用途：测试线程有界等待；入参为闩锁，无出参，中断或超时均明确失败，不用睡眠控制顺序。 */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(20, TimeUnit.SECONDS), "隔离并发场景闩锁未释放");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("隔离并发场景被中断", exception);
        }
    }

    /** 用途：释放场景线程；入参为执行器，无出参，确认退出后才允许清理随机数据库。 */
    private static void stopWorkers(ExecutorService workers) throws InterruptedException {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "并发测试线程未退出");
    }

    /** 带受检异常的数据库场景回调，仅服务本文件隔离库生命周期。 */
    private interface DatabaseScenario {
        /** 执行一条场景；入参为正式 PostgreSQL 端口夹具，无出参。 */
        void run(Fixture fixture) throws Exception;
    }

    /** 所有事实端口与 Spring 事务管理器使用同一 DataSource，模拟生产事务归属而不启动完整应用。 */
    private static final class Fixture {
        private final DataSource dataSource;
        private final JdbcTemplate jdbc;
        private final DataSourceTransactionManager transactions;
        private final PostgresIotRepository repository;
        private final PostgresTelemetryStore store;
        private final PostgresAlarmRepository alarmRepository;
        private final AlarmApplicationServiceImpl alarms;
        private final UUID tenantId = UUID.randomUUID();
        private final UUID profileId = UUID.randomUUID();
        private final UUID firstDevice = UUID.randomUUID();
        private final UUID secondDevice = UUID.randomUUID();
        private final UUID ruleId = UUID.randomUUID();

        /** 用途：装配正式端口并创建独立模型/设备/规则；入参为随机库 DataSource，构造完整摄取夹具。 */
        private Fixture(DataSource dataSource) {
            this.dataSource = dataSource;
            jdbc = new JdbcTemplate(dataSource);
            transactions = new DataSourceTransactionManager(dataSource);
            repository = new PostgresIotRepository(jdbc);
            store = new PostgresTelemetryStore(jdbc);
            alarmRepository = new PostgresAlarmRepository(jdbc);
            PostgresAlarmContextLinkRepository contextRepository = new PostgresAlarmContextLinkRepository(jdbc);
            AlarmContextLinkApplicationService context = transactionalProxy(
                    new AlarmContextLinkApplicationServiceImpl(contextRepository, (tenant, device, time) -> {
                        throw new AssertionError("遥测事务不得同步查询 Core");
                    }), AlarmContextLinkApplicationService.class, transactions);
            alarms = new AlarmApplicationServiceImpl(alarmRepository,
                    new DeviceProfileAlarmRuleFactsAdapter(repository, repository),
                    new IotIdempotencyExecutor(new InMemoryIdempotencyStorage(), new ObjectMapper()), store, context);
            UUID operator = UUID.randomUUID();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                repository.insert(new DeviceProfile(profileId, tenantId, "machine", "并发测试机型", "ACTIVE", 60,
                        List.of(new DeviceProfile.MetricDefinition("temperature", "温度", MetricValueType.NUMBER, "C", true)),
                        operator, BASE_TIME, operator, BASE_TIME));
                repository.insert(device(firstDevice, "A", operator));
                repository.insert(device(secondDevice, "B", operator));
                repository.insertRule(new AlarmRule(ruleId, tenantId, "hot", profileId, null, "temperature", "GT",
                        new BigDecimal("40"), new BigDecimal("35"), "High", "ACTIVE", operator, BASE_TIME));
            });
        }

        /** 用途：构造一个独立锁表的 Spring 摄取代理；入参为可注入端口和事务管理器，出参为事务化应用接口。 */
        private TelemetryIngestionService service(TelemetryFactPort facts, TelemetryAlarmPort alarmPort,
                                                  PlatformTransactionManager manager) {
            return transactionalProxy(new TelemetryIngestionServiceImpl(repository, repository, store, facts, store, alarmPort),
                    TelemetryIngestionService.class, manager);
        }

        /** 用途：生成设备夹具；入参为标识、后缀和审计用户，出参为当前租户启用的 MQTT 设备。 */
        private Device device(UUID id, String suffix, UUID operator) {
            return new Device(id, tenantId, "machine-" + suffix, "并发测试设备", profileId, "MQTT", DeviceLifecycleStatus.Active,
                    null, null, null, operator, BASE_TIME, operator, BASE_TIME);
        }

        /** 用途：生成一条可触发/恢复阈值的消息；入参为设备、键、时间、数值及摘要字符，出参为可信命令。 */
        private TelemetryIngestionCommand command(UUID device, String key, OffsetDateTime timestamp,
                                                   String temperature, char hashCharacter) {
            String code = firstDevice.equals(device) ? "machine-A" : "machine-B";
            return new TelemetryIngestionCommand(new TelemetryCredentialContext(tenantId, device, "test-credential"),
                    device, code, timestamp, timestamp.plusSeconds(1), key, null,
                    List.of(new TelemetryMetric("temperature", temperature, "C")), String.valueOf(hashCharacter).repeat(64));
        }

        /** 用途：核对五类事实数量；入参为设备及各类预期计数，无出参，查询均带租户/设备范围。 */
        private void assertCounts(UUID device, int dedup, int facts, int statuses, int alarmCount, int tasks) {
            assertEquals(dedup, count("iot_message_dedup", device));
            assertEquals(facts, count("iot_device_telemetry", device));
            assertEquals(statuses, count("iot_device_status", device));
            assertEquals(alarmCount, count("iot_device_alarm", device));
            assertEquals(tasks, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM iot_alarm_context_task AS task
                    JOIN iot_device_alarm AS alarm ON alarm.tenant_id = task.tenant_id AND alarm.id = task.alarm_id
                    WHERE alarm.tenant_id = ? AND alarm.device_id = ?
                    """, Integer.class, tenantId, device));
        }

        /** 用途：统计固定白名单表的设备事实；入参为表名和设备，出参为数量，禁止任意 SQL 名称。 */
        private int count(String table, UUID device) {
            assertTrue(List.of("iot_message_dedup", "iot_device_telemetry", "iot_device_status", "iot_device_alarm").contains(table));
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND device_id = ?",
                    Integer.class, tenantId, device);
        }
    }

    /** 用途：复用 Spring 注解事务代理；入参为目标、接口和同源事务管理器，出参为代理，不安装额外框架。 */
    private static <T> T transactionalProxy(T target, Class<T> type, PlatformTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setInterfaces(type);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }

    /** 暂停真实 JDBC commit，覆盖 Spring 代理在应用方法返回后才提交的时间窗口。 */
    private static final class CommitGateTransactionManager extends DataSourceTransactionManager {
        private final CountDownLatch commitEntered;
        private final CountDownLatch allowCommit;
        private final AtomicBoolean pauseFirst = new AtomicBoolean(true);

        /** 用途：设置首次提交闩锁；入参为 DataSource 与闩锁，构造有界暂停的真实 JDBC 管理器。 */
        private CommitGateTransactionManager(DataSource source, CountDownLatch commitEntered, CountDownLatch allowCommit) {
            super(source);
            this.commitEntered = commitEntered;
            this.allowCommit = allowCommit;
        }

        /** 首次提交先通知测试并等待，放行后执行原 JDBC commit，随后由 Spring 调用 afterCompletion。 */
        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (pauseFirst.compareAndSet(true, false)) {
                commitEntered.countDown();
                await(allowCommit);
            }
            super.doCommit(status);
        }
    }
}
