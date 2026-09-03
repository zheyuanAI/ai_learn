package com.ailearn.platform.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * PostgreSQL 12.1 Auth 真实迁移验证。
 * <p>
 * 测试只连接调用方显式传入且已预先启动的独立 PostgreSQL 12.1 验证实例。每个用例创建随机
 * auth_migration_* 数据库，迁移执行只发生在该随机库内；本类不启动 PostgreSQL、不连接开发库、
 * 不使用 H2 替代真实迁移。
 * </p>
 */
class AuthPostgresMigrationTest {

    private static final String DEFAULT_TENANT_ID = "a0000000-0000-0000-0000-000000000001";
    private static final String TENANT_B_ID = "a0000000-0000-0000-0000-000000000002";
    private static final String DEFAULT_ADMIN_USER_ID = "c0000000-0000-0000-0000-000000000001";
    private static final String DEFAULT_ADMIN_ROLE_ID = "b0000000-0000-0000-0000-000000000001";
    private static final String PG12_URL_PROPERTY = "auth.test.pg12.jdbc-url";
    private static final String PG12_USER_PROPERTY = "auth.test.pg12.username";
    private static final String PG12_PASSWORD_PROPERTY = "auth.test.pg12.password";
    private static final List<String> AUTH_V1_V4_SCRIPTS = List.of(
            "V1__init_auth_schema.sql",
            "V2__seed_auth_demo_data.sql",
            "V3__repair_demo_password_hash.sql",
            "V4__add_admin_menus_and_permissions.sql"
    );

    private static String adminJdbcUrl;
    private static String adminUsername;
    private static String adminPassword;
    private static JdbcUrlParts adminJdbcUrlParts;

    /**
     * 校验调用方提供的外部 PostgreSQL 12 验证实例配置。
     *
     * @throws SQLException 连接失败、版本不符或目标库不安全时抛出异常，使测试明确失败
     */
    @BeforeAll
    static void verifyExternalPostgres12Configuration() throws SQLException {
        adminJdbcUrl = requiredConfiguredValue(PG12_URL_PROPERTY);
        adminUsername = requiredConfiguredValue(PG12_USER_PROPERTY);
        adminPassword = requiredConfiguredValue(PG12_PASSWORD_PROPERTY);
        adminJdbcUrlParts = parseJdbcUrl(adminJdbcUrl);
        assertSafeAdminDatabase(adminJdbcUrlParts);

        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword)) {
            String serverVersionNumber = readString(connection, "SHOW server_version_num");
            assertTrue(serverVersionNumber.startsWith("12"),
                    "迁移测试必须运行在 PostgreSQL 12.x，实际 server_version_num: " + serverVersionNumber);
            DatabaseMetaData metaData = connection.getMetaData();
            assertEquals(12, metaData.getDatabaseMajorVersion(),
                    "迁移测试必须运行在 PostgreSQL 12.x，实际元数据版本: "
                            + metaData.getDatabaseProductVersion());
        }
    }

    @Test
    void freshDatabaseMigratesV1ThroughV5AndKeepsTenantIntegrity() throws Exception {
        String databaseName = createDatabase();
        try {
            String jdbcUrl = databaseJdbcUrl(databaseName);
            Flyway.configure()
                    .dataSource(jdbcUrl, adminUsername, adminPassword)
                    .locations("classpath:db/migration/auth")
                    .table("auth_flyway_schema_history")
                    .baselineOnMigrate(false)
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUsername, adminPassword)) {
                assertConnectedDatabase(connection, databaseName);
                assertMigrationHistory(connection, "auth_flyway_schema_history", 5, "5");
                assertMenuSchemaAndTenantData(connection);
                assertDefaultAdminStillHasMenus(connection);
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void v1ThroughV4DatabaseCanBeHandedOffWithoutTouchingSharedHistoryAndThenRunOnlyV5() throws Exception {
        String databaseName = createDatabase();
        try {
            String jdbcUrl = databaseJdbcUrl(databaseName);
            Flyway.configure()
                    .dataSource(jdbcUrl, adminUsername, adminPassword)
                    .locations("classpath:db/migration/auth")
                    .table("flyway_schema_history")
                    .baselineOnMigrate(false)
                    .target(MigrationVersion.fromVersion("4"))
                    .load()
                    .migrate();

            List<HistoryRow> sharedHistoryBefore;
            try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUsername, adminPassword)) {
                assertConnectedDatabase(connection, databaseName);
                assertMigrationHistory(connection, "flyway_schema_history", 4, "4");
                insertSimulatedCoreAndIotHistoryRows(connection);
                sharedHistoryBefore = readHistoryRows(connection, "flyway_schema_history");
                executeSqlScript(connection, locateHistoryHandoffScript());
                assertHistoryRowsEqual(sharedHistoryBefore, readHistoryRows(connection, "flyway_schema_history"));
                assertOnlyConfirmedAuthRowsCopied(connection, sharedHistoryBefore);
            }

            Flyway.configure()
                    .dataSource(jdbcUrl, adminUsername, adminPassword)
                    .locations("classpath:db/migration/auth")
                    .table("auth_flyway_schema_history")
                    .baselineOnMigrate(false)
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUsername, adminPassword)) {
                assertConnectedDatabase(connection, databaseName);
                assertHistoryRowsEqual(sharedHistoryBefore, readHistoryRows(connection, "flyway_schema_history"));
                assertMigrationHistory(connection, "auth_flyway_schema_history", 5, "5");
                assertEquals(1, scalarInt(connection,
                        "SELECT COUNT(*) FROM auth_flyway_schema_history "
                                + "WHERE version = '5' AND script = 'V5__menu_tenant_isolation_and_visible_status.sql' "
                                + "AND success = TRUE"));
                assertEquals(0, scalarInt(connection,
                        "SELECT COUNT(*) FROM flyway_schema_history "
                                + "WHERE script = 'V5__menu_tenant_isolation_and_visible_status.sql'"));
                assertMenuSchemaAndTenantData(connection);
                assertDefaultAdminStillHasMenus(connection);
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void handoffFailureRollsBackWithoutLeavingPartialTargetHistoryTable() throws Exception {
        String databaseName = createDatabase();
        try {
            String jdbcUrl = databaseJdbcUrl(databaseName);
            Flyway.configure()
                    .dataSource(jdbcUrl, adminUsername, adminPassword)
                    .locations("classpath:db/migration/auth")
                    .table("flyway_schema_history")
                    .baselineOnMigrate(false)
                    .target(MigrationVersion.fromVersion("4"))
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUsername, adminPassword)) {
                assertConnectedDatabase(connection, databaseName);
                insertFailedAuthHistoryRow(connection);
            }

            assertThrows(SQLException.class, () -> {
                try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUsername, adminPassword)) {
                    executeSqlScript(connection, locateHistoryHandoffScript());
                }
            });

            try (Connection connection = DriverManager.getConnection(jdbcUrl, adminUsername, adminPassword)) {
                assertConnectedDatabase(connection, databaseName);
                assertEquals(0, scalarInt(connection,
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = 'public' AND table_name = 'auth_flyway_schema_history'"),
                        "handoff 失败后不应留下半成品 Auth 历史表");
            }
        } finally {
            dropDatabase(databaseName);
        }
    }

    @Test
    void schemaPgSqlFileDoesNotExistAndIsNotReferenced() throws IOException {
        Path repositoryRoot = locateRepositoryRoot();
        Path deletedSchema = repositoryRoot.resolve("backend/platform-auth/src/main/resources/db/schema-pg.sql");
        assertFalse(Files.exists(deletedSchema), "schema-pg.sql 已无运行时或测试调用方，应删除");

        List<Path> references = new ArrayList<>();
        try (var paths = Files.walk(repositoryRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(repositoryRoot.resolve(".git")))
                    .filter(path -> !path.endsWith("AuthPostgresMigrationTest.java"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .forEach(path -> collectSchemaPgReferences(path, references));
        }
        assertTrue(references.isEmpty(), "schema-pg.sql 不应再被引用: " + references);
    }

    /**
     * 断言当前连接已经切换到本测试创建的随机数据库。
     *
     * @param connection 当前 JDBC 连接
     * @param expectedDatabaseName 期望连接到的随机数据库名
     * @throws SQLException 查询当前数据库失败时抛出
     */
    private static void assertConnectedDatabase(Connection connection, String expectedDatabaseName) throws SQLException {
        assertEquals(expectedDatabaseName, readString(connection, "SELECT current_database()"));
        assertTrue(expectedDatabaseName.startsWith("auth_migration_"));
        assertFalse("ai_learn".equalsIgnoreCase(expectedDatabaseName), "迁移测试禁止连接 ai_learn 开发库");
    }

    /**
     * 断言迁移历史表版本数与最终成功版本。
     *
     * @param connection 测试数据库连接
     * @param tableName Flyway 历史表名
     * @param versionCount 期望成功版本数
     * @param maxVersion 期望的最大版本号
     * @throws SQLException 查询历史表失败时抛出
     */
    private static void assertMigrationHistory(Connection connection, String tableName, int versionCount,
                                               String maxVersion) throws SQLException {
        String sql = "SELECT COUNT(*), COALESCE(MAX(version), '') FROM " + tableName + " WHERE success = TRUE";
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            assertEquals(versionCount, resultSet.getInt(1));
            assertEquals(maxVersion, resultSet.getString(2));
        }
    }

    /**
     * 断言 V5 的真实 PostgreSQL 表结构、约束及两个租户的同编码菜单数据。
     *
     * @param connection 测试数据库连接
     * @throws SQLException 查询表结构或数据失败时抛出
     */
    private static void assertMenuSchemaAndTenantData(Connection connection) throws SQLException {
        assertEquals("NO", readString(connection,
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'auth_menu' AND column_name = 'tenant_id'"));
        assertEquals(1, scalarInt(connection,
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'public.auth_menu'::regclass "
                        + "AND conname = 'ck_auth_menu_visible_allowed'"));
        assertEquals(1, scalarInt(connection,
                "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'public.auth_menu'::regclass "
                        + "AND conname = 'ck_auth_menu_status_allowed'"));
        assertEquals(1, scalarInt(connection,
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname = 'uq_auth_menu_tenant_code_active'"));

        assertTrue(scalarInt(connection,
                "SELECT COUNT(*) FROM auth_menu WHERE tenant_id = '" + DEFAULT_TENANT_ID + "'::uuid") > 0);
        assertTrue(scalarInt(connection,
                "SELECT COUNT(*) FROM auth_menu WHERE tenant_id = '" + TENANT_B_ID + "'::uuid") > 0);
        assertTrue(scalarInt(connection,
                "SELECT COUNT(*) FROM (SELECT menu_code FROM auth_menu WHERE isdel = 0 "
                        + "GROUP BY menu_code HAVING COUNT(DISTINCT tenant_id) = 2) codes") > 0,
                "两个租户应允许相同 menu_code");
        assertEquals(0, scalarInt(connection,
                "SELECT COUNT(*) FROM auth_role r JOIN auth_role_menu rm ON rm.role_id = r.id "
                        + "JOIN auth_menu m ON m.id = rm.menu_id "
                        + "WHERE r.id = 'b2000000-0000-0000-0000-000000000001'::uuid "
                        + "AND (r.tenant_id <> m.tenant_id OR r.tenant_id <> '" + TENANT_B_ID + "'::uuid)"));
    }

    /**
     * 断言 V2 默认租户管理员升级后仍然能够通过角色菜单关系查到菜单。
     *
     * @param connection 测试数据库连接
     * @throws SQLException 查询菜单授权关系失败时抛出
     */
    private static void assertDefaultAdminStillHasMenus(Connection connection) throws SQLException {
        assertTrue(scalarInt(connection,
                "SELECT COUNT(*) FROM auth_user_role ur "
                        + "JOIN auth_role_menu rm ON rm.role_id = ur.role_id AND rm.isdel = 0 "
                        + "JOIN auth_menu m ON m.id = rm.menu_id "
                        + "WHERE ur.user_id = '" + DEFAULT_ADMIN_USER_ID + "'::uuid "
                        + "AND ur.tenant_id = '" + DEFAULT_TENANT_ID + "'::uuid "
                        + "AND ur.isdel = 0 AND m.tenant_id = '" + DEFAULT_TENANT_ID + "'::uuid "
                        + "AND m.isdel = 0") > 0);
        assertEquals(0, scalarInt(connection,
                "SELECT COUNT(*) FROM auth_menu WHERE tenant_id IS NULL"));
        assertEquals(1, scalarInt(connection,
                "SELECT COUNT(*) FROM auth_role WHERE id = '" + DEFAULT_ADMIN_ROLE_ID + "'::uuid "
                        + "AND tenant_id = '" + DEFAULT_TENANT_ID + "'::uuid"));
    }

    /**
     * 在共享 Flyway 历史表中插入模拟 Core 与 IoT 成功记录。
     *
     * @param connection 随机测试数据库连接
     * @throws SQLException 插入模拟记录失败时抛出
     */
    private static void insertSimulatedCoreAndIotHistoryRows(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO flyway_schema_history "
                        + "(installed_rank, version, description, type, script, checksum, installed_by, "
                        + "installed_on, execution_time, success) "
                        + "VALUES (?, ?, ?, 'SQL', ?, ?, ?, CURRENT_TIMESTAMP, ?, TRUE)")) {
            insertSimulatedHistoryRow(statement, 101, "1", "init core schema",
                    "V1__init_core_schema.sql", 100001, "core");
            insertSimulatedHistoryRow(statement, 102, "1", "init iot schema",
                    "V1__init_iot_schema.sql", 100002, "iot");
        }
    }

    /**
     * 向批量语句中追加一条模拟模块历史记录。
     *
     * @param statement 已准备的共享历史表插入语句
     * @param installedRank 模拟记录 installed_rank
     * @param version 模拟记录版本
     * @param description 模拟记录描述
     * @param script 模拟记录脚本名
     * @param checksum 模拟记录校验和
     * @param installedBy 模拟记录安装账号
     * @throws SQLException 设置参数或执行插入失败时抛出
     */
    private static void insertSimulatedHistoryRow(PreparedStatement statement, int installedRank, String version,
                                                  String description, String script, int checksum,
                                                  String installedBy) throws SQLException {
        statement.setInt(1, installedRank);
        statement.setString(2, version);
        statement.setString(3, description);
        statement.setString(4, script);
        statement.setInt(5, checksum);
        statement.setString(6, installedBy);
        statement.setInt(7, 1);
        statement.executeUpdate();
    }

    /**
     * 插入一条 Auth 失败历史，用于触发 handoff 事务失败并验证回滚。
     *
     * @param connection 随机测试数据库连接
     * @throws SQLException 插入失败历史记录失败时抛出
     */
    private static void insertFailedAuthHistoryRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO flyway_schema_history "
                        + "(installed_rank, version, description, type, script, checksum, installed_by, "
                        + "installed_on, execution_time, success) "
                        + "VALUES (99, '4', 'failed auth duplicate', 'SQL', "
                        + "'V4__add_admin_menus_and_permissions.sql', 999, 'auth', CURRENT_TIMESTAMP, 1, FALSE)")) {
            statement.executeUpdate();
        }
    }

    /**
     * 断言接管脚本只复制确认属于 Auth 的成功 V1-V4 记录，且完整保留字段。
     *
     * @param connection 随机测试数据库连接
     * @param sharedHistoryBefore 接管前共享历史表完整快照
     * @throws SQLException 查询 Auth 历史表失败时抛出
     */
    private static void assertOnlyConfirmedAuthRowsCopied(Connection connection, List<HistoryRow> sharedHistoryBefore)
            throws SQLException {
        List<HistoryRow> expectedAuthRows = sharedHistoryBefore.stream()
                .filter(row -> AUTH_V1_V4_SCRIPTS.contains(row.script()) && row.success())
                .toList();
        List<HistoryRow> actualAuthRows = readHistoryRows(connection, "auth_flyway_schema_history");
        assertHistoryRowsEqual(expectedAuthRows, actualAuthRows);
        assertEquals(0, actualAuthRows.stream()
                .filter(row -> row.script().contains("core") || row.script().contains("iot"))
                .count(), "Core/IoT 历史记录不能复制到 Auth 历史表");
    }

    /**
     * 创建随机测试数据库。
     *
     * @return 随机数据库名
     * @throws SQLException 创建数据库失败时抛出
     */
    private static String createDatabase() throws SQLException {
        String databaseName = "auth_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        }
        return databaseName;
    }

    /**
     * 终止随机库残留连接并删除随机测试数据库。
     *
     * @param databaseName 随机数据库名
     * @throws SQLException 终止连接或删除数据库失败时抛出，使清理失败明确暴露
     */
    private static void dropDatabase(String databaseName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword);
             PreparedStatement terminate = connection.prepareStatement(
                     "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                             + "WHERE datname = ? AND pid <> pg_backend_pid()");
             Statement drop = connection.createStatement()) {
            terminate.setString(1, databaseName);
            terminate.execute();
            drop.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName));
        } catch (SQLException exception) {
            fail("清理 PostgreSQL 随机测试数据库失败: " + databaseName, exception);
        }
    }

    /**
     * 基于管理库 JDBC 地址构造随机测试数据库 JDBC 地址。
     *
     * @param databaseName 随机数据库名
     * @return 指向随机测试数据库的 JDBC 地址
     */
    private static String databaseJdbcUrl(String databaseName) {
        return adminJdbcUrlParts.withDatabase(databaseName);
    }

    /**
     * 读取必填 JVM 系统属性。
     *
     * @param propertyName JVM 系统属性名称
     * @return 去除首尾空白后的属性值
     */
    private static String requiredConfiguredValue(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少必填 PostgreSQL 12.1 迁移测试配置: -D" + propertyName);
        }
        return value.trim();
    }

    /**
     * 校验管理连接的目标库不是项目开发库。
     *
     * @param jdbcUrlParts 已解析的 JDBC URL 片段
     */
    private static void assertSafeAdminDatabase(JdbcUrlParts jdbcUrlParts) {
        assertFalse("ai_learn".equalsIgnoreCase(jdbcUrlParts.databaseName()),
                "迁移测试禁止连接 ai_learn 开发库");
    }

    /**
     * 解析 PostgreSQL JDBC URL，保留查询参数用于构造随机库 URL。
     *
     * @param jdbcUrl 原始 JDBC URL
     * @return 解析后的 URL 片段
     */
    private static JdbcUrlParts parseJdbcUrl(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("只支持 PostgreSQL JDBC URL: " + jdbcUrl);
        }
        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            String databaseName = path == null || path.length() <= 1 ? "" : path.substring(1);
            if (databaseName.isBlank()) {
                throw new IllegalArgumentException("PostgreSQL JDBC URL 必须显式指定管理数据库名");
            }
            String prefix = "jdbc:postgresql://" + uri.getAuthority() + "/";
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return new JdbcUrlParts(prefix, databaseName, query);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("无法解析 PostgreSQL JDBC URL: " + jdbcUrl, exception);
        }
    }

    /**
     * 定位仓库中的历史表接管脚本。
     *
     * @return 接管脚本路径
     */
    private static Path locateHistoryHandoffScript() {
        Path candidate = locateRepositoryRoot().resolve("deploy/postgres/auth-flyway-history-handoff.sql");
        if (Files.exists(candidate)) {
            return candidate;
        }
        throw new IllegalStateException("未找到 auth-flyway-history-handoff.sql");
    }

    /**
     * 定位当前仓库根目录。
     *
     * @return 仓库根目录
     */
    private static Path locateRepositoryRoot() {
        Path root = Path.of("").toAbsolutePath().normalize();
        while (root != null) {
            if (Files.exists(root.resolve("deploy/postgres/auth-flyway-history-handoff.sql"))) {
                return root;
            }
            root = root.getParent();
        }
        throw new IllegalStateException("未找到当前仓库根目录");
    }

    /**
     * 执行 handoff SQL 文件。
     *
     * @param connection PostgreSQL 连接
     * @param scriptPath SQL 脚本路径
     * @throws IOException 读取脚本失败时抛出
     * @throws SQLException PostgreSQL 执行脚本失败时抛出
     */
    private static void executeSqlScript(Connection connection, Path scriptPath) throws IOException, SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(scriptPath));
        }
    }

    /**
     * 读取 Flyway 历史表快照。
     *
     * @param connection PostgreSQL 连接
     * @param tableName 历史表名
     * @return 按 installed_rank 排序的历史表行
     * @throws SQLException 查询历史表失败时抛出
     */
    private static List<HistoryRow> readHistoryRows(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT installed_rank, version, description, type, script, checksum, installed_by, "
                + "installed_on, execution_time, success FROM " + tableName + " ORDER BY installed_rank";
        List<HistoryRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(new HistoryRow(
                        resultSet.getInt("installed_rank"),
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getString("type"),
                        resultSet.getString("script"),
                        resultSet.getObject("checksum", Integer.class),
                        resultSet.getString("installed_by"),
                        resultSet.getString("installed_on"),
                        resultSet.getInt("execution_time"),
                        resultSet.getBoolean("success")));
            }
        }
        return rows;
    }

    /**
     * 比较两份 Flyway 历史表快照。
     *
     * @param expected 期望快照
     * @param actual 实际快照
     */
    private static void assertHistoryRowsEqual(List<HistoryRow> expected, List<HistoryRow> actual) {
        assertEquals(expected, actual);
    }

    /**
     * 扫描文件内容并收集 schema-pg.sql 静态引用。
     *
     * @param path 待扫描文件
     * @param references 命中的引用文件集合
     */
    private static void collectSchemaPgReferences(Path path, List<Path> references) {
        try {
            String content = Files.readString(path);
            String lowered = content.toLowerCase(Locale.ROOT);
            if (lowered.contains("schema-pg.sql") || lowered.contains("schema-pg")) {
                references.add(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // 二进制或不可按文本读取的文件不参与 schema-pg.sql 静态引用断言。
        }
    }

    /**
     * 执行返回单个字符串的只读 SQL。
     *
     * @param connection PostgreSQL 连接
     * @param sql 查询 SQL
     * @return 第一行第一列字符串
     * @throws SQLException 查询失败时抛出
     */
    private static String readString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    /**
     * 执行返回单个整数的只读 SQL。
     *
     * @param connection PostgreSQL 连接
     * @param sql 查询 SQL
     * @return 第一行第一列整数
     * @throws SQLException 查询失败时抛出
     */
    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    /**
     * 对数据库标识符做双引号转义。
     *
     * @param identifier 数据库标识符
     * @return 已转义标识符
     */
    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record JdbcUrlParts(String prefix, String databaseName, String query) {

        /**
         * 构造指向指定数据库的 JDBC URL。
         *
         * @param newDatabaseName 新数据库名
         * @return 替换数据库名后的 JDBC URL
         */
        String withDatabase(String newDatabaseName) {
            return prefix + newDatabaseName + query;
        }
    }

    private record HistoryRow(
            int installedRank,
            String version,
            String description,
            String type,
            String script,
            Integer checksum,
            String installedBy,
            String installedOn,
            int executionTime,
            boolean success) {
    }
}
