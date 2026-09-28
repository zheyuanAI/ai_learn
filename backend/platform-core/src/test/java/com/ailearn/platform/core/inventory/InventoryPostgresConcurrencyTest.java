package com.ailearn.platform.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.core.config.UuidTypeHandler;
import com.ailearn.platform.core.inventory.application.InventoryApplicationService;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.core.inventory.infrastructure.CoreIdempotencyMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryAllocationMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryBalanceMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryReservationMapper;
import com.ailearn.platform.core.inventory.infrastructure.InventoryTransactionMapper;
import com.ailearn.platform.core.inventory.infrastructure.PostgresCoreIdempotencyStorage;
import com.ailearn.platform.core.inventory.infrastructure.PostgresInventoryRepository;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.sales.dto.PickLineRequest;
import com.ailearn.platform.core.sales.dto.PickTaskConfirmRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnLineRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnRequest;
import com.ailearn.platform.core.sales.dto.ShipmentConfirmRequest;
import com.ailearn.platform.core.sales.dto.ShipmentLineRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.fulfillment.application.SalesFulfillmentApplicationServiceImpl;
import com.ailearn.platform.core.sales.infrastructure.PostgresSalesOrderRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 可选真实 PostgreSQL 库存锁序与事务回滚回归。
 * 仅接受显式 core.test.pg12 参数，在随机隔离库执行正式迁移；不回退到项目配置，不连接开发 5433/ai_learn。
 * 验证正确性与固定交错，不代表吞吐压测或完整业务 E2E。
 */
class InventoryPostgresConcurrencyTest {
    private static String adminUrl;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static String databaseName;
    private static DriverManagerDataSource dataSource;
    private static DataSourceTransactionManager transactionManager;
    private static JdbcTemplate jdbc;
    private static SqlSessionTemplate session;

    /**
     * 入参：显式隔离实例系统参数；出参：隔离库及真实事务/Mapper 环境；
     * 流程：缺参数跳过，拒绝开发端口和库，检查 PG12，然后只迁移随机新库。
     */
    @BeforeAll
    static void prepareIsolatedDatabase() throws Exception {
        adminUrl = System.getProperty("core.test.pg12.jdbc-url");
        username = System.getProperty("core.test.pg12.username");
        password = System.getProperty("core.test.pg12.password", "");
        Assumptions.assumeTrue(adminUrl != null && !adminUrl.isBlank()
                && username != null && !username.isBlank(), "未提供隔离 PG12 参数，跳过真实库存并发测试");
        assertTrue(adminUrl.startsWith("jdbc:postgresql://"), "必须提供 PostgreSQL JDBC 管理库地址");
        URI uri = URI.create(adminUrl.substring("jdbc:".length()));
        assertTrue(uri.getHost() != null && uri.getPath() != null && uri.getPath().length() > 1,
                "隔离 JDBC URL 必须包含主机和管理库");
        assertFalse(uri.getPort() == 5433, "库存并发测试禁止连接开发端口 5433");
        assertFalse("ai_learn".equalsIgnoreCase(uri.getPath().substring(1)), "禁止连接 ai_learn 开发库");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password)) {
            assertEquals(12, connection.getMetaData().getDatabaseMajorVersion(), "隔离实例必须为 PostgreSQL 12");
        }
        databaseName = "core_inventory_lock_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + databaseName + "\"");
        }
        jdbcUrl = "jdbc:postgresql://" + uri.getRawAuthority() + "/" + databaseName
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        Flyway.configure().dataSource(jdbcUrl, username, password).locations("classpath:db/migration/core")
                .table("core_flyway_schema_history").baselineOnMigrate(false).load().migrate();
        dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        transactionManager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        Configuration configuration = new Configuration(new Environment("inventory-pg-test",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        configuration.setDefaultStatementTimeout(10);
        configuration.addMapper(InventoryBalanceMapper.class);
        configuration.addMapper(InventoryReservationMapper.class);
        configuration.addMapper(InventoryAllocationMapper.class);
        configuration.addMapper(InventoryTransactionMapper.class);
        configuration.addMapper(CoreIdempotencyMapper.class);
        session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
    }

    /** 入参：无；出参：无；流程：全部连接结束后仅清理本测试创建的随机库，不删除任何开发业务记录。 */
    @AfterAll
    static void cleanupIsolatedDatabase() throws SQLException {
        if (databaseName == null) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\"");
        }
    }

    /**
     * 入参：无；出参：无；流程：两个真实连接各持一个相反余额，再同时请求对方余额，固定复现旧锁序 40P01。
     * 回滚结束后余额保持原值；此用例不把超时或连接异常误判为死锁证据。
     */
    @Test
    void oldPartialLockOrderProducesPostgresDeadlock() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstLocksHeld = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var forward = workers.submit(() -> reverseLock(fixture, fixture.source(), fixture.target(), firstLocksHeld));
            var backward = workers.submit(() -> reverseLock(fixture, fixture.target(), fixture.source(), firstLocksHeld));
            List<String> outcomes = List.of(forward.get(15, TimeUnit.SECONDS), backward.get(15, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("40P01"::equals).count());
            assertEquals(1, outcomes.stream().filter("LOCKED"::equals).count());
            assertAmount(fixture, fixture.source(), "10");
            assertAmount(fixture, fixture.target(), "10");
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS), "数据库锁线程未结束");
        }
    }

    /**
     * 入参：无；出参：无；流程：反向请求经真实应用端口在外层事务预锁全集合，再执行真实库存移动。
     * 验证两事务均提交、余额总量与两条流水一致，并验证 MANDATORY 不允许脱离外层事务预锁。
     */
    @Test
    void completePrelockLetsOppositeTransactionsCommit() throws Exception {
        Fixture fixture = fixture();
        setActor(fixture);
        try {
            assertThrows(IllegalTransactionStateException.class,
                    () -> fixture.service().lockBalances(List.of(fixture.source(), fixture.target())));
        } finally {
            UserContextHolder.clear();
        }
        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var forward = workers.submit(() -> prelockedMove(fixture, fixture.source(), fixture.target(), "2", ready));
            var backward = workers.submit(() -> prelockedMove(fixture, fixture.target(), fixture.source(), "1", ready));
            assertEquals("MOVE", forward.get(15, TimeUnit.SECONDS));
            assertEquals("MOVE", backward.get(15, TimeUnit.SECONDS));
            assertAmount(fixture, fixture.source(), "9");
            assertAmount(fixture, fixture.target(), "11");
            assertEquals(2, count("inv_inventory_transaction", fixture.tenant()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM core_idempotency_record "
                    + "WHERE tenant_id=? AND status='SUCCESS' AND isdel=0", Integer.class, fixture.tenant()));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS), "数据库锁线程未结束");
        }
    }

    /**
     * 入参：无；出参：无；流程：幂等 SUCCESS 的 beforeCommit 后故意失败，验证新余额/流水/记录同事务回滚。
     * 再用相同命令和幂等键重试成功，证明生产 PG 存储不会保留纯内存 fallback 边界中的假成功。
     */
    @Test
    void failureAfterIdempotencyBeforeCommitRollsBackEverythingAndAllowsRetry() {
        Fixture fixture = fixture();
        InventoryDimension newDimension = new InventoryDimension(fixture.source().productId(),
                fixture.source().warehouseId(), fixture.target().locationId(), "LOT-NEW");
        setActor(fixture);
        try {
            InventoryIncreaseCommand command = new InventoryIncreaseCommand(metadata(fixture, "rollback-key", "RECEIPT"),
                    newDimension, new BigDecimal("3"));
            assertThrows(IllegalStateException.class, () -> transaction().execute(status -> {
                fixture.service().lockBalances(List.of(fixture.source(), newDimension));
                fixture.service().increase(command);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    /** 在库存幂等完成回调之后拒绝提交，固定验证真正的提交阶段回滚。 */
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM core_idempotency_record "
                                + "WHERE tenant_id=? AND status='SUCCESS' AND isdel=0", Integer.class, fixture.tenant()));
                        throw new IllegalStateException("测试故意拒绝提交");
                    }
                });
                return null;
            }));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inv_inventory_balance "
                    + "WHERE tenant_id=? AND lot_no='LOT-NEW' AND isdel=0", Integer.class, fixture.tenant()));
            assertEquals(0, count("inv_inventory_transaction", fixture.tenant()));
            assertEquals(0, count("core_idempotency_record", fixture.tenant()));
            assertAmount(fixture, fixture.source(), "10");
            transaction().execute(status -> {
                fixture.service().lockBalances(List.of(fixture.source(), newDimension));
                return fixture.service().increase(command);
            });
            assertAmount(fixture, newDimension, "3");
            assertEquals(1, count("inv_inventory_transaction", fixture.tenant()));
            assertEquals(1, count("core_idempotency_record", fixture.tenant()));
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 入参：无；出参：无；流程：真实销售与库存服务执行拣51→退4→发47→再拣66→发66。
     * 第二次拣货同时使用旧分配4与新分配62，必须保存两条事实且父键重放不得重复写入；发货同样覆盖多分配。
     */
    @Test
    void salesPickAndShipmentPersistMultipleAllocationsForOneOrderLine() {
        Fixture fixture = fixture();
        setActor(fixture);
        try {
            jdbc.update("UPDATE inv_inventory_balance SET on_hand_qty=137 WHERE tenant_id=? AND location_id=? AND isdel=0",
                    fixture.tenant(), fixture.source().locationId());
            jdbc.update("UPDATE inv_inventory_balance SET on_hand_qty=0 WHERE tenant_id=? AND location_id=? AND isdel=0",
                    fixture.tenant(), fixture.target().locationId());
            UUID orderId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            OffsetDateTime at = OffsetDateTime.now();
            SalesOrderLine line = new SalesOrderLine(lineId, fixture.tenant(), 1, fixture.source().productId(), "EA",
                    new BigDecimal("137"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            SalesOrder order = new SalesOrder(orderId, fixture.tenant(), "SO-MULTI", UUID.randomUUID(), null,
                    SalesOrderStatus.Approved, null, null, null, null, null, null, 0L,
                    fixture.user(), at, fixture.user(), at, List.of(line));
            PostgresSalesOrderRepository orders = new PostgresSalesOrderRepository(dataSource);
            transaction().execute(status -> orders.insert(order));
            SalesFulfillmentApplicationServiceImpl raw = new SalesFulfillmentApplicationServiceImpl(orders,
                    fixture.service(), fixture.service(), (tenantId, locationId) -> fixture.tenant().equals(tenantId)
                    ? new LocationSnapshot(locationId, tenantId, fixture.source().warehouseId(),
                    fixture.source().locationId().equals(locationId) ? LocationType.Storage : LocationType.ShippingStaging, "ACTIVE")
                    : null, new PostgresCoreIdempotencyStorage(session.getMapper(CoreIdempotencyMapper.class)),
                    new ObjectMapper().registerModule(new JavaTimeModule()));
            ProxyFactory proxy = new ProxyFactory(raw);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
            SalesFulfillmentApplicationServiceImpl sales = (SalesFulfillmentApplicationServiceImpl) proxy.getProxy();
            sales.confirmPick(pickRequest(fixture, orderId, lineId, "51"), "sales-pick-1");
            PickTaskReturnLineRequest returnedLine = new PickTaskReturnLineRequest();
            returnedLine.setSalesOrderLineId(lineId);
            returnedLine.setReturnQty("4");
            returnedLine.setToLocationId(fixture.source().locationId());
            PickTaskReturnRequest returned = new PickTaskReturnRequest();
            returned.setSalesOrderId(orderId);
            returned.setLines(List.of(returnedLine));
            sales.returnPick(returned, "sales-return-1");
            sales.confirmShipment(shipmentRequest(fixture, orderId, lineId, "47"), "sales-ship-1");
            PickTaskConfirmRequest secondPick = pickRequest(fixture, orderId, lineId, "66");
            var picked = sales.confirmPick(secondPick, "sales-pick-2");
            assertEquals("113.000000", picked.order().getLines().getFirst().pickedQty());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sales_fulfillment_fact "
                    + "WHERE tenant_id=? AND operation_id=? AND action_type='PICK'", Integer.class,
                    fixture.tenant(), picked.operationId()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT allocation_id) FROM sales_fulfillment_fact "
                    + "WHERE tenant_id=? AND operation_id=? AND action_type='PICK'", Integer.class,
                    fixture.tenant(), picked.operationId()));
            sales.confirmPick(secondPick, "sales-pick-2");
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM sales_fulfillment_fact WHERE tenant_id=?",
                    Integer.class, fixture.tenant()));
            var shipped = sales.confirmShipment(shipmentRequest(fixture, orderId, lineId, "66"), "sales-ship-2");
            assertEquals("113.000000", shipped.order().getLines().getFirst().shippedQty());
            assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM sales_fulfillment_fact WHERE tenant_id=?",
                    Integer.class, fixture.tenant()));
            assertEquals(12, count("inv_inventory_transaction", fixture.tenant()));
            assertAmount(fixture, fixture.source(), "24");
            assertAmount(fixture, fixture.target(), "0");
            SalesOrderCompleteRequest complete = new SalesOrderCompleteRequest();
            complete.setCompletionReason("剩余24件不再履约");
            assertEquals("Completed", sales.manuallyComplete(orderId, complete, "sales-complete").order().getStatus());
        } finally {
            UserContextHolder.clear();
        }
    }

    /** 入参：测试事实、订单/行和数量；出参：直接拣货请求；流程：复现同一来源与暂存批次的实际角色请求。 */
    private PickTaskConfirmRequest pickRequest(Fixture fixture, UUID orderId, UUID lineId, String quantity) {
        PickLineRequest line = new PickLineRequest();
        line.setSalesOrderLineId(lineId);
        line.setPickedQty(quantity);
        line.setSourceLocationId(fixture.source().locationId());
        line.setShippingLocationId(fixture.target().locationId());
        PickTaskConfirmRequest request = new PickTaskConfirmRequest();
        request.setSalesOrderId(orderId);
        request.setLines(List.of(line));
        return request;
    }

    /** 入参：测试事实、订单/行和数量；出参：发货请求；流程：固定本次时间与产品，交给真实服务释放并扣减。 */
    private ShipmentConfirmRequest shipmentRequest(Fixture fixture, UUID orderId, UUID lineId, String quantity) {
        ShipmentLineRequest line = new ShipmentLineRequest();
        line.setSalesOrderLineId(lineId);
        line.setProductId(fixture.source().productId());
        line.setShipQty(quantity);
        ShipmentConfirmRequest request = new ShipmentConfirmRequest();
        request.setSalesOrderId(orderId);
        request.setShipmentLines(List.of(line));
        request.setShipTime(OffsetDateTime.now());
        return request;
    }

    /** 入参：测试事实；出参：旧锁序的数据库结果；流程：每个连接先占一锁、屏障后请求另一锁并回滚。 */
    private String reverseLock(Fixture fixture, InventoryDimension first, InventoryDimension second,
                               CountDownLatch held) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET LOCAL statement_timeout='10s'");
                }
                lockRow(connection, fixture.tenant(), first);
                held.countDown();
                assertTrue(held.await(5, TimeUnit.SECONDS), "首轮余额锁屏障未完成");
                lockRow(connection, fixture.tenant(), second);
                return "LOCKED";
            } catch (SQLException exception) {
                if (!"40P01".equals(exception.getSQLState())) {
                    throw exception;
                }
                return exception.getSQLState();
            } finally {
                connection.rollback();
            }
        }
    }

    /** 入参：连接、租户与完整维度；出参：无；流程：使用与生产 Mapper 相同的有效租户维度 FOR UPDATE。 */
    private void lockRow(Connection connection, UUID tenant, InventoryDimension dimension) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM inv_inventory_balance "
                + "WHERE tenant_id=? AND product_id=? AND warehouse_id=? AND location_id=? AND lot_no=? "
                + "AND isdel=0 FOR UPDATE")) {
            statement.setObject(1, tenant);
            statement.setObject(2, dimension.productId());
            statement.setObject(3, dimension.warehouseId());
            statement.setObject(4, dimension.locationId());
            statement.setString(5, dimension.lotNo());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next(), "待锁余额不存在");
            }
        }
    }

    /** 入参：事实、方向、数量与起跑屏障；出参：库存动作；流程：先预锁整集合，再真实移动并提交外层事务。 */
    private String prelockedMove(Fixture fixture, InventoryDimension from, InventoryDimension to,
                                  String quantity, CountDownLatch ready) throws Exception {
        setActor(fixture);
        try {
            ready.countDown();
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发请求起跑屏障未完成");
            return transaction().execute(status -> {
                fixture.service().lockBalances(List.of(from, to));
                return fixture.service().move(new InventoryMoveCommand(metadata(fixture,
                        "move-" + from.locationId(), "TRANSFER"), from, to, new BigDecimal(quantity))).operation();
            });
        } finally {
            UserContextHolder.clear();
        }
    }

    /** 入参：无；出参：真实外层事务模板；流程：固定十秒锁等待保护，避免回归失败永久挂起。 */
    private TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setTimeout(10);
        return template;
    }

    /** 入参：无；出参：独立租户事实与真实库存服务代理；流程：按正式字段建主数据、余额并接入五个 Mapper。 */
    private Fixture fixture() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID sourceLocation = UUID.fromString("e1000000-0000-0000-0000-000000000002");
        UUID targetLocation = UUID.fromString("e1000000-0000-0000-0000-000000000001");
        // 库位主键全库唯一；每个用例以新产品/仓库/租户搭配独立库位，保持 source 稳定键大于 target。
        sourceLocation = new UUID(sourceLocation.getMostSignificantBits(), UUID.randomUUID().getLeastSignificantBits());
        targetLocation = new UUID(targetLocation.getMostSignificantBits(), UUID.randomUUID().getLeastSignificantBits());
        if (sourceLocation.toString().compareTo(targetLocation.toString()) < 0) {
            UUID swap = sourceLocation;
            sourceLocation = targetLocation;
            targetLocation = swap;
        }
        jdbc.update("INSERT INTO md_product(id,tenant_id,sku,name,uom,created_by) VALUES(?,?,?,?,?,?)",
                product, tenant, "TEST-SKU", "锁序测试产品", "EA", user);
        jdbc.update("INSERT INTO md_warehouse(id,tenant_id,code,name,created_by) VALUES(?,?,?,?,?)",
                warehouse, tenant, "TEST-WH", "锁序测试仓库", user);
        jdbc.update("INSERT INTO md_location(id,tenant_id,warehouse_id,code,name,type,created_by) VALUES(?,?,?,?,?,?,?)",
                sourceLocation, tenant, warehouse, "SOURCE", "来源", "Storage", user);
        jdbc.update("INSERT INTO md_location(id,tenant_id,warehouse_id,code,name,type,created_by) VALUES(?,?,?,?,?,?,?)",
                targetLocation, tenant, warehouse, "TARGET", "暂存", "ShippingStaging", user);
        InventoryDimension source = new InventoryDimension(product, warehouse, sourceLocation, "LOT-1");
        InventoryDimension target = new InventoryDimension(product, warehouse, targetLocation, "LOT-1");
        for (InventoryDimension dimension : List.of(source, target)) {
            jdbc.update("INSERT INTO inv_inventory_balance(id,tenant_id,product_id,warehouse_id,location_id,lot_no,"
                            + "on_hand_qty,reserved_qty,created_by) VALUES(?,?,?,?,?,?,10,0,?)",
                    UUID.randomUUID(), tenant, product, warehouse, dimension.locationId(), dimension.lotNo(), user);
        }
        PostgresInventoryRepository repository = new PostgresInventoryRepository(session.getMapper(InventoryBalanceMapper.class),
                session.getMapper(InventoryReservationMapper.class), session.getMapper(InventoryAllocationMapper.class),
                session.getMapper(InventoryTransactionMapper.class));
        InventoryApplicationService raw = new InventoryApplicationService(repository,
                (tenantId, locationId) -> tenant.equals(tenantId)
                        && (source.locationId().equals(locationId) || target.locationId().equals(locationId))
                        ? new LocationSnapshot(locationId, tenant, warehouse,
                        source.locationId().equals(locationId) ? LocationType.Storage : LocationType.ShippingStaging, "ACTIVE")
                        : null,
                new PostgresCoreIdempotencyStorage(session.getMapper(CoreIdempotencyMapper.class)),
                new ObjectMapper().registerModule(new JavaTimeModule()));
        ProxyFactory proxy = new ProxyFactory(raw);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return new Fixture(tenant, user, source, target, (InventoryApplicationService) proxy.getProxy());
    }

    /** 入参：事实；出参：无；流程：设置与命令一致的服务端可信上下文。 */
    private void setActor(Fixture fixture) {
        TenantContextHolder.setTenantId(fixture.tenant());
        RequestContextHolder.getContext().setUserId(fixture.user());
        RequestContextHolder.getContext().setJti("pg-lock-session");
        RequestContextHolder.getContext().setRequestId("pg-lock-request");
    }

    /** 入参：事实、幂等键和既有交易类型；出参：可信库存元数据；流程：保持本用例同键重试载荷稳定。 */
    private InventoryCommandMetadata metadata(Fixture fixture, String key, String type) {
        return new InventoryCommandMetadata(fixture.tenant(), fixture.user(), "pg-lock-session", "pg-lock-request",
                key, "pg-lock-digest", "TRANSFER", fixture.source().productId(), null, type, OffsetDateTime.now());
    }

    /** 入参：事实、维度和期望实物；出参：无；流程：显式按租户与完整维度验证有效余额。 */
    private void assertAmount(Fixture fixture, InventoryDimension dimension, String expected) {
        BigDecimal amount = jdbc.queryForObject("SELECT on_hand_qty FROM inv_inventory_balance "
                        + "WHERE tenant_id=? AND product_id=? AND warehouse_id=? AND location_id=? AND lot_no=? AND isdel=0",
                BigDecimal.class, fixture.tenant(), dimension.productId(), dimension.warehouseId(),
                dimension.locationId(), dimension.lotNo());
        assertEquals(0, new BigDecimal(expected).compareTo(amount));
    }

    /** 入参：固定测试表名与租户；出参：有效记录数；流程：只使用测试内部常量表名，强制租户与软删条件。 */
    private int count(String table, UUID tenant) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND isdel=0", Integer.class, tenant);
    }

    /** 每个测试使用独立租户，避免并发测试之间共享余额或幂等事实。 */
    private record Fixture(UUID tenant, UUID user, InventoryDimension source, InventoryDimension target,
                           InventoryApplicationService service) { }
}
