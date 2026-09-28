package com.ailearn.platform.core.purchasing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLineCumulative;
import com.ailearn.platform.core.purchasing.infrastructure.PostgresPurchaseOrderRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 采购订单行五项累计的可选真实 PostgreSQL 12 回归。
 * 仅使用显式提供的隔离管理库参数，创建随机数据库并执行正式 Core Flyway 迁移。
 */
class PostgresPurchaseOrderLineCumulativeTest {
    private static String adminUrl;
    private static String username;
    private static String password;
    private static String databaseName;
    private static JdbcTemplate jdbc;
    private static PostgresPurchaseOrderRepository repository;

    /**
     * 校验显式隔离 PostgreSQL 12 参数，并在随机数据库运行正式迁移。
     * 入参：core.test.pg12 系统属性；出参：测试独有的 JDBC 与 Repository；
     * 流程：缺配置则跳过，拒绝开发库和开发端口，再创建随机库并迁移。
     */
    @BeforeAll
    static void prepareIsolatedDatabase() throws SQLException {
        adminUrl = System.getProperty("core.test.pg12.jdbc-url");
        username = System.getProperty("core.test.pg12.username");
        password = System.getProperty("core.test.pg12.password", "");
        Assumptions.assumeTrue(adminUrl != null && !adminUrl.isBlank()
                        && username != null && !username.isBlank(),
                "未提供隔离 PG12 参数，跳过采购累计真实库测试");
        assertTrue(adminUrl.startsWith("jdbc:postgresql://"), "必须提供 PostgreSQL 管理库 JDBC URL");
        URI uri = URI.create(adminUrl.substring("jdbc:".length()));
        assertTrue(uri.getHost() != null && uri.getPath() != null && uri.getPath().length() > 1,
                "隔离 JDBC URL 必须包含主机和管理库");
        assertFalse(uri.getPort() == 5433, "采购累计测试禁止连接开发端口 5433");
        assertFalse("ai_learn".equalsIgnoreCase(uri.getPath().substring(1)), "禁止连接 ai_learn 开发库");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password)) {
            assertEquals(12, connection.getMetaData().getDatabaseMajorVersion(), "隔离实例必须为 PostgreSQL 12");
        }
        String createdName = "core_purchase_cumulative_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + createdName + "\"");
        }
        databaseName = createdName;
        String jdbcUrl = "jdbc:postgresql://" + uri.getRawAuthority() + "/" + databaseName
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        Flyway.configure().dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration/core")
                .table("core_flyway_schema_history").baselineOnMigrate(false).load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        jdbc = new JdbcTemplate(dataSource);
        repository = new PostgresPurchaseOrderRepository(dataSource);
    }

    /**
     * 只清理本测试成功创建的随机数据库。
     * 入参：无；出参：无；流程：通过隔离管理库删除测试库，不接触开发业务库。
     */
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
     * 验证五项累计严格沿采购行和收货明细关联，且各独立事实不发生乘算。
     * 入参：无；出参：无；流程：构造同 SKU 多行多批、多个质检/处置/上架事实，
     * 再叠加各级软删、非最终状态、其他订单和租户，并核对每一行的精确累计。
     */
    @Test
    void cumulativeFactsStayOnTheirOrderLineAndExcludeInactiveChains() {
        // 修改原因：详情跨历史单据读取时必须保留订单回溯及上架明细的有效事实索引。
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema()
                  AND indexname IN ('ix_purchase_receipt_order_confirmed',
                                    'ix_putaway_task_receipt_line_confirmed')
                """, Integer.class));
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        Site site = insertSite(tenant);
        Site otherSite = insertSite(otherTenant);
        UUID order = UUID.randomUUID();
        UUID otherOrder = UUID.randomUUID();
        UUID foreignOrder = UUID.randomUUID();
        UUID firstLine = UUID.randomUUID();
        UUID secondLine = UUID.randomUUID();
        UUID emptyLine = UUID.randomUUID();
        UUID deletedLine = UUID.randomUUID();
        UUID otherOrderLine = UUID.randomUUID();
        UUID foreignLine = UUID.randomUUID();
        insertOrder(tenant, order);
        insertOrder(tenant, otherOrder);
        insertOrder(otherTenant, foreignOrder);
        // 修改原因：相同 SKU 的不同采购行必须分别归集，不能以产品或批次作为回溯键。
        insertOrderLine(tenant, order, firstLine, 1, site, false);
        insertOrderLine(tenant, order, secondLine, 2, site, false);
        insertOrderLine(tenant, order, emptyLine, 3, site, false);
        insertOrderLine(tenant, order, deletedLine, 4, site, true);
        insertOrderLine(tenant, otherOrder, otherOrderLine, 1, site, false);
        insertOrderLine(otherTenant, foreignOrder, foreignLine, 1, otherSite, false);

        UUID firstReceipt = insertReceipt(tenant, order, site, "Confirmed", false);
        UUID secondReceipt = insertReceipt(tenant, order, site, "Confirmed", false);
        UUID firstReceiptLine = insertReceiptLine(tenant, firstReceipt, firstLine, 1, site, 10, 2, 8, "LOT-A", false);
        UUID secondOrderReceiptLine = insertReceiptLine(tenant, firstReceipt, secondLine, 2, site, 5, 0, 5, "LOT-A", false);
        UUID secondReceiptLine = insertReceiptLine(tenant, secondReceipt, firstLine, 1, site, 7, 1, 6, "LOT-B", false);
        UUID firstInspection = insertInspection(tenant, firstReceipt, firstReceiptLine, site, 6, 5, 1, "PendingDecision", false);
        UUID additionalInspection = insertInspection(tenant, firstReceipt, firstReceiptLine, site, 2, 1, 1, "PendingDecision", false);
        UUID secondInspection = insertInspection(tenant, secondReceipt, secondReceiptLine, site, 6, 5, 1, "Completed", false);
        UUID otherLineInspection = insertInspection(tenant, firstReceipt, secondOrderReceiptLine, site, 5, 4, 1, "PendingDecision", false);
        insertDisposition(tenant, firstInspection, "Release", 3, "Completed", false);
        insertDisposition(tenant, firstInspection, "Release", 1, "PendingExecution", false);
        insertDisposition(tenant, additionalInspection, "Release", 1, "Completed", false);
        insertDisposition(tenant, secondInspection, "Release", 3, "Completed", false);
        insertDisposition(tenant, secondInspection, "Return", 1, "Completed", false);
        insertDisposition(tenant, otherLineInspection, "Release", 2, "Completed", false);
        insertPutaway(tenant, firstReceipt, firstReceiptLine, site, 3, "Confirmed", false);
        insertPutaway(tenant, firstReceipt, firstReceiptLine, site, 1, "Pending", false);
        insertPutaway(tenant, secondReceipt, secondReceiptLine, site, 2, "Confirmed", false);
        insertPutaway(tenant, firstReceipt, secondOrderReceiptLine, site, 2, "Confirmed", false);

        // 各级已删节点及未确认收货都保留子事实，以检验查询对整条有效链的过滤。
        UUID draftReceipt = insertReceipt(tenant, order, site, "Draft", false);
        UUID draftReceiptLine = insertReceiptLine(tenant, draftReceipt, firstLine, 1, site, 3, 0, 3, "LOT-C", false);
        UUID draftInspection = insertInspection(tenant, draftReceipt, draftReceiptLine, site, 3, 3, 0, "PendingDecision", false);
        insertDisposition(tenant, draftInspection, "Release", 3, "Completed", false);
        insertPutaway(tenant, draftReceipt, draftReceiptLine, site, 3, "Confirmed", false);

        UUID deletedReceipt = insertReceipt(tenant, order, site, "Confirmed", true);
        UUID deletedReceiptLine = insertReceiptLine(tenant, deletedReceipt, firstLine, 1, site, 3, 0, 3, "LOT-D", false);
        UUID deletedReceiptInspection = insertInspection(tenant, deletedReceipt, deletedReceiptLine, site, 3, 3, 0, "PendingDecision", false);
        insertDisposition(tenant, deletedReceiptInspection, "Release", 3, "Completed", false);
        insertPutaway(tenant, deletedReceipt, deletedReceiptLine, site, 3, "Confirmed", false);

        UUID lineDeletedReceipt = insertReceipt(tenant, order, site, "Confirmed", false);
        UUID inactiveReceiptLine = insertReceiptLine(tenant, lineDeletedReceipt, firstLine, 1, site, 3, 0, 3, "LOT-E", true);
        UUID inactiveLineInspection = insertInspection(tenant, lineDeletedReceipt, inactiveReceiptLine, site, 3, 3, 0, "PendingDecision", false);
        insertDisposition(tenant, inactiveLineInspection, "Release", 3, "Completed", false);
        insertPutaway(tenant, lineDeletedReceipt, inactiveReceiptLine, site, 3, "Confirmed", false);

        UUID inactiveInspection = insertInspection(tenant, firstReceipt, firstReceiptLine, site, 1, 1, 0, "PendingDecision", true);
        insertDisposition(tenant, inactiveInspection, "Release", 1, "Completed", false);
        insertDisposition(tenant, firstInspection, "Release", 1, "Completed", true);
        insertPutaway(tenant, firstReceipt, firstReceiptLine, site, 1, "Confirmed", true);

        // 两个外键各自有效仍不足以证明是同一收货链，错配的子事实不得归到采购行。
        UUID mismatchedInspection = insertInspection(tenant, secondReceipt, firstReceiptLine, site,
                1, 1, 0, "PendingDecision", false);
        insertDisposition(tenant, mismatchedInspection, "Release", 1, "Completed", false);
        insertPutaway(tenant, secondReceipt, firstReceiptLine, site, 1, "Confirmed", false);

        UUID unrelatedReceipt = insertReceipt(tenant, otherOrder, site, "Confirmed", false);
        insertReceiptLine(tenant, unrelatedReceipt, otherOrderLine, 1, site, 50, 0, 50, "LOT-X", false);
        insertReceiptLine(tenant, unrelatedReceipt, firstLine, 2, site, 40, 0, 40, "LOT-X", false);
        UUID foreignReceipt = insertReceipt(otherTenant, foreignOrder, otherSite, "Confirmed", false);
        insertReceiptLine(otherTenant, foreignReceipt, foreignLine, 1, otherSite, 20, 1, 19, "LOT-A", false);

        Map<UUID, PurchaseOrderLineCumulative> totals = repository.findLineCumulatives(tenant, order);
        assertEquals(3, totals.size(), "仅返回本订单有效采购行");
        assertCumulative(totals.get(firstLine), 17, 3, 11, 7, 5);
        assertCumulative(totals.get(secondLine), 5, 0, 4, 2, 2);
        assertCumulative(totals.get(emptyLine), 0, 0, 0, 0, 0);
        assertFalse(totals.containsKey(deletedLine));
        assertFalse(totals.containsKey(otherOrderLine));
        assertFalse(totals.containsKey(foreignLine));
        assertTrue(repository.findLineCumulatives(otherTenant, order).isEmpty(), "跨租户订单不可见");
        assertCumulative(repository.findLineCumulatives(otherTenant, foreignOrder).get(foreignLine),
                20, 1, 0, 0, 0);
        jdbc.update("UPDATE purchase_order SET isdel = 1 WHERE tenant_id = ? AND id = ?", tenant, otherOrder);
        assertTrue(repository.findLineCumulatives(tenant, otherOrder).isEmpty(), "已删订单不可见");
    }

    /**
     * 写入当前租户同 SKU 的最小产品、仓库及质量隔离/暂存/存储库位。
     * 入参：租户 ID；出参：后续采购事实的引用 ID；流程：按外键依赖顺序插入主数据。
     */
    private Site insertSite(UUID tenant) {
        UUID product = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID hold = UUID.randomUUID();
        UUID staging = UUID.randomUUID();
        UUID storage = UUID.randomUUID();
        jdbc.update("INSERT INTO md_product (id, tenant_id, sku, name, uom, created_by) VALUES (?, ?, 'SAME-SKU', '测试物料', 'PCS', ?)",
                product, tenant, tenant);
        jdbc.update("INSERT INTO md_warehouse (id, tenant_id, code, name, created_by) VALUES (?, ?, 'W1', '测试仓库', ?)",
                warehouse, tenant, tenant);
        jdbc.update("INSERT INTO md_location (id, tenant_id, warehouse_id, code, name, type, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                hold, tenant, warehouse, "HOLD", "隔离位", "QualityHold", tenant);
        jdbc.update("INSERT INTO md_location (id, tenant_id, warehouse_id, code, name, type, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                staging, tenant, warehouse, "STAGING", "暂存位", "ReceivingStaging", tenant);
        jdbc.update("INSERT INTO md_location (id, tenant_id, warehouse_id, code, name, type, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                storage, tenant, warehouse, "STORAGE", "存储位", "Storage", tenant);
        return new Site(product, warehouse, hold, staging, storage);
    }

    /**
     * 插入一个采购订单头。
     * 入参：租户与订单 ID；出参：无；流程：保存满足 V3 必填字段的已审批订单。
     */
    private void insertOrder(UUID tenant, UUID order) {
        jdbc.update("INSERT INTO purchase_order (id, tenant_id, po_no, supplier_id, expected_arrival_date, status, created_by) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE, 'Approved', ?)",
                order, tenant, "PO-" + order, UUID.randomUUID(), tenant);
    }

    /**
     * 插入一个采购订单行，可标记逻辑删除。
     * 入参：租户、订单/行 ID、行号、主数据及删除标识；出参：无；流程：保留相同产品的独立行身份。
     */
    private void insertOrderLine(UUID tenant, UUID order, UUID line, int lineNo, Site site, boolean deleted) {
        jdbc.update("INSERT INTO purchase_order_line (id, tenant_id, purchase_order_id, line_no, product_id, uom, "
                        + "ordered_qty, target_warehouse_id, created_by, isdel) VALUES (?, ?, ?, ?, ?, 'PCS', 100, ?, ?, ?)",
                line, tenant, order, lineNo, site.product(), site.warehouse(), tenant, deleted ? 1 : 0);
    }

    /**
     * 插入一张收货单，分别满足 Draft 与 Confirmed 的确认审计约束。
     * 入参：租户、订单、库位、状态及删除标识；出参：收货单 ID；流程：写入独立批次的收货头。
     */
    private UUID insertReceipt(UUID tenant, UUID order, Site site, String status, boolean deleted) {
        UUID receipt = UUID.randomUUID();
        boolean confirmed = "Confirmed".equals(status);
        OffsetDateTime confirmedAt = confirmed ? OffsetDateTime.now() : null;
        jdbc.update("INSERT INTO purchase_receipt (id, tenant_id, receipt_no, purchase_order_id, receipt_time, "
                        + "quality_hold_location_id, status, confirmed_by, confirmed_session_id, confirmed_at, "
                        + "created_by, isdel) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)",
                receipt, tenant, "PR-" + receipt, order, site.hold(), status,
                confirmed ? tenant : null, confirmed ? "isolated-test" : null, confirmedAt,
                tenant, deleted ? 1 : 0);
        return receipt;
    }

    /**
     * 插入一条指向明确采购行的收货明细。
     * 入参：租户、收货/采购行、行号、产品、三种数量、批次和删除标识；出参：收货明细 ID；
     * 流程：利用数据库数量校验固定到货等于拒收加实收。
     */
    private UUID insertReceiptLine(UUID tenant, UUID receipt, UUID orderLine, int lineNo, Site site,
                                   int arrived, int rejected, int received, String lot, boolean deleted) {
        UUID receiptLine = UUID.randomUUID();
        jdbc.update("INSERT INTO purchase_receipt_line (id, tenant_id, purchase_receipt_id, purchase_order_line_id, "
                        + "line_no, product_id, uom, arrived_qty, rejected_qty, received_qty, lot_no, "
                        + "rejection_reason, created_by, isdel) VALUES (?, ?, ?, ?, ?, ?, 'PCS', ?, ?, ?, ?, ?, ?, ?)",
                receiptLine, tenant, receipt, orderLine, lineNo, site.product(), arrived, rejected, received, lot,
                rejected > 0 ? "外观拒收" : null, tenant, deleted ? 1 : 0);
        return receiptLine;
    }

    /**
     * 插入一条采购收货质检事实，可标记逻辑删除。
     * 入参：租户、收货与明细 ID、产品、三种质检数量、状态和删除标识；出参：质检 ID；
     * 流程：在同一明细上允许多个质检事实，以检验独立聚合。
     */
    private UUID insertInspection(UUID tenant, UUID receipt, UUID receiptLine, Site site,
                                  int inspected, int qualified, int unqualified, String status, boolean deleted) {
        UUID inspection = UUID.randomUUID();
        jdbc.update("INSERT INTO purchase_quality_inspection (id, tenant_id, purchase_receipt_id, "
                        + "purchase_receipt_line_id, product_id, inspected_qty, qualified_qty, unqualified_qty, "
                        + "status, inspected_by, inspected_at, created_by, isdel) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                inspection, tenant, receipt, receiptLine, site.product(), inspected, qualified, unqualified,
                status, tenant, tenant, deleted ? 1 : 0);
        return inspection;
    }

    /**
     * 插入一项质量处置决定或已执行事实。
     * 入参：租户、质检 ID、类型、数量、状态和删除标识；出参：无；
     * 流程：完成态填执行审计，待执行态仅保留决定事实。
     */
    private void insertDisposition(UUID tenant, UUID inspection, String type, int quantity,
                                   String status, boolean deleted) {
        boolean completed = "Completed".equals(status);
        jdbc.update("INSERT INTO purchase_quality_disposition (id, tenant_id, purchase_quality_inspection_id, "
                        + "disposition_type, disposition_qty, status, decided_by, decided_at, executed_by, "
                        + "executed_at, created_by, isdel) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)",
                UUID.randomUUID(), tenant, inspection, type, quantity, status, tenant,
                completed ? tenant : null, completed ? OffsetDateTime.now() : null, tenant, deleted ? 1 : 0);
    }

    /**
     * 插入一个绑定收货明细的上架任务。
     * 入参：租户、收货/明细、主数据、数量、状态和删除标识；出参：无；
     * 流程：仅确认态填确认审计，保留待执行任务以验证状态过滤。
     */
    private void insertPutaway(UUID tenant, UUID receipt, UUID receiptLine, Site site,
                               int quantity, String status, boolean deleted) {
        boolean confirmed = "Confirmed".equals(status);
        UUID task = UUID.randomUUID();
        jdbc.update("INSERT INTO putaway_task (id, tenant_id, task_no, purchase_receipt_id, "
                        + "purchase_receipt_line_id, product_id, from_location_id, to_location_id, putaway_qty, "
                        + "status, confirmed_by, confirmed_at, created_by, isdel) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                task, tenant, "PUT-" + task, receipt, receiptLine, site.product(), site.staging(), site.storage(),
                quantity, status, confirmed ? tenant : null, confirmed ? OffsetDateTime.now() : null,
                tenant, deleted ? 1 : 0);
    }

    /**
     * 用数值比较核对五项累计，避免 NUMERIC(19,6) 与 Java 常量的小数位差异。
     * 入参：实际累计和到货、拒收、合格、放行执行、上架预期整数；出参：无；
     * 流程：先确认行存在，再逐字段比较数值。
     */
    private void assertCumulative(PurchaseOrderLineCumulative actual, int arrived, int rejected,
                                  int qualified, int releaseExecuted, int putaway) {
        assertNotNull(actual, "有效采购行必须返回零值或累计值");
        assertAmount(arrived, actual.arrivedQty());
        assertAmount(rejected, actual.rejectedQty());
        assertAmount(qualified, actual.qualifiedQty());
        assertAmount(releaseExecuted, actual.releaseExecutedQty());
        assertAmount(putaway, actual.putawayQty());
    }

    /**
     * 比较 NUMERIC 数值而不要求固定 Java BigDecimal scale。
     * 入参：预期整数和实际数据库累计；出参：无；流程：按数值相等断言。
     */
    private void assertAmount(int expected, BigDecimal actual) {
        assertNotNull(actual, "累计数量不得为 null");
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual));
    }

    private record Site(UUID product, UUID warehouse, UUID hold, UUID staging, UUID storage) {
    }
}
