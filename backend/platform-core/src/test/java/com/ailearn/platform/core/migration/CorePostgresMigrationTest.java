package com.ailearn.platform.core.migration;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core PostgreSQL 12 隔离迁移测试。
 * <p>
 * 只有明确提供隔离管理库参数时才执行；未提供时跳过，绝不回退到项目开发库。
 * </p>
 */
class CorePostgresMigrationTest {

    private static final String URL_PROPERTY = "core.test.pg12.jdbc-url";
    private static final String USER_PROPERTY = "core.test.pg12.username";
    private static final String PASSWORD_PROPERTY = "core.test.pg12.password";
    private static final List<String> REQUIRED_TABLES = List.of(
            "md_uom", "md_product", "md_customer", "md_supplier", "md_warehouse", "md_location",
            "inv_inventory_balance", "inv_inventory_reservation", "inv_inventory_reservation_allocation",
            "inv_inventory_transaction", "inv_transfer_order", "inv_transfer_order_line",
            "inv_stocktake_order", "inv_stocktake_order_line", "core_idempotency_record");

    private static String adminUrl;
    private static String username;
    private static String password;
    private static JdbcUrlParts urlParts;

    /**
     * 校验调用方提供的隔离 PostgreSQL 12 实例；缺少配置时安全跳过。
     * 入参：系统属性；出参：无；流程：解析 URL、拒绝开发库并检查 PostgreSQL 主版本。
     *
     * @throws SQLException 外部隔离实例连接或版本校验失败
     */
    @BeforeAll
    static void verifyExternalPostgres12Configuration() throws SQLException {
        adminUrl = System.getProperty(URL_PROPERTY);
        username = System.getProperty(USER_PROPERTY);
        password = System.getProperty(PASSWORD_PROPERTY, "");
        Assumptions.assumeTrue(adminUrl != null && !adminUrl.isBlank()
                        && username != null && !username.isBlank(),
                "未提供 Core 隔离 PostgreSQL 12 配置，跳过真实迁移测试");
        urlParts = parseJdbcUrl(adminUrl.trim());
        assertFalse("ai_learn".equalsIgnoreCase(urlParts.databaseName()),
                "Core 迁移测试禁止连接 ai_learn 开发库");
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password)) {
            String version = readString(connection, "SHOW server_version_num");
            assertTrue(version.startsWith("12"), "迁移测试必须运行在 PostgreSQL 12.x: " + version);
            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals(12, metadata.getDatabaseMajorVersion(), "PostgreSQL 主版本必须为 12");
        }
    }

    /**
     * 在随机隔离数据库中执行 V1→V2，并验证核心表和余额约束。
     * 入参：无；出参：无；流程：创建随机库、运行 Flyway、读取结构后清理随机库。
     *
     * @throws Exception 迁移或隔离数据库清理失败
     */
    @Test
    void freshDatabaseMigratesV1ToV2AndCreatesTenantSafeCoreTables() throws Exception {
        String databaseName = "core_migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            createDatabase(databaseName);
            String jdbcUrl = urlParts.withDatabase(databaseName);
            Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .locations("classpath:db/migration/core")
                    .table("core_flyway_schema_history")
                    .baselineOnMigrate(false)
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                assertEquals(databaseName, readString(connection, "SELECT current_database()"));
                assertFalse("ai_learn".equalsIgnoreCase(readString(connection, "SELECT current_database()")));
                for (String table : REQUIRED_TABLES) {
                    assertEquals(1, scalarInt(connection,
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE table_schema='public' AND table_name='" + table + "'"),
                            "缺少 Core 表: " + table);
                }
                assertEquals(2, scalarInt(connection,
                        "SELECT COUNT(*) FROM core_flyway_schema_history WHERE success = TRUE"));
                assertEquals(1, scalarInt(connection,
                        "SELECT COUNT(*) FROM pg_constraint "
                                + "WHERE conrelid='public.inv_inventory_balance'::regclass "
                                + "AND conname='ck_inv_balance_nonnegative'"));
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    private static void createDatabase(String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + databaseName + "\"");
        }
    }

    private static void dropDatabase(String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS \"" + databaseName + "\"");
        }
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static String readString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private static JdbcUrlParts parseJdbcUrl(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("只支持 PostgreSQL JDBC URL: " + jdbcUrl);
        }
        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            String databaseName = path == null || path.length() <= 1 ? "" : path.substring(1);
            Assumptions.assumeTrue(!databaseName.isBlank(), "隔离 PostgreSQL URL 必须带管理库名");
            String prefix = "jdbc:postgresql://" + uri.getAuthority() + "/";
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return new JdbcUrlParts(prefix, databaseName, query);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("无法解析 PostgreSQL JDBC URL: " + jdbcUrl, exception);
        }
    }

    private record JdbcUrlParts(String prefix, String databaseName, String query) {
        private String withDatabase(String database) {
            return prefix + database + query;
        }
    }
}
