package com.ailearn.platform.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auth 数据库迁移脚本静态校验。
 */
class AuthMigrationScriptTest {

    private static final Pattern UUID_LITERAL = Pattern.compile("'([^']+)'::uuid");
    private static final Pattern BCRYPT_LITERAL =
            Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'");
    private static final String LEGACY_DEMO_PASSWORD_HASH =
            "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";
    private static final String FIXED_DEMO_PASSWORD_HASH =
            "$2a$10$ojmRH0hpaeHIKF5u8/0ZCOjtRHCpfJTonxo7AmJ7sJav6pqeTZ0/2";

    /**
     * 校验 V2 种子脚本中的 UUID 字面量均为 PostgreSQL 可接受的 UUID。
     * 入参：无；出参：无；流程：读取迁移资源，提取显式 UUID 转换并逐个交给 JDK UUID 解析器校验。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V2 种子数据中的 UUID 字面量必须合法")
    void shouldContainOnlyValidUuidLiterals() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/auth/V2__seed_auth_demo_data.sql")) {
            assertNotNull(input, "V2 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = UUID_LITERAL.matcher(sql);
            int literalCount = 0;

            while (matcher.find()) {
                String literal = matcher.group(1);
                assertDoesNotThrow(() -> UUID.fromString(literal), "非法 UUID: " + literal);
                literalCount++;
            }

            assertTrue(literalCount > 0, "V2 迁移脚本至少应包含一个 UUID 字面量");
        }
    }

    /**
     * 校验 V3 修复迁移提供的 BCrypt 密文确实对应文档约定的 123456。
     * 入参：无；出参：无；流程：读取 V3 迁移资源，确认只针对历史错误密文进行修复，且目标密文匹配明文。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V3 修复后的演示账号密码哈希必须匹配 123456")
    void shouldUseValidDemoPasswordHash() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/auth/V3__repair_demo_password_hash.sql")) {
            assertNotNull(input, "V3 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = BCRYPT_LITERAL.matcher(sql);
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

            assertTrue(sql.contains(LEGACY_DEMO_PASSWORD_HASH), "V3 必须定位历史错误密文");
            assertTrue(sql.contains(FIXED_DEMO_PASSWORD_HASH), "V3 必须提供修复后的密文");
            assertTrue(passwordEncoder.matches("123456", FIXED_DEMO_PASSWORD_HASH),
                    "V3 修复后的演示账号密码哈希不匹配 123456");
            assertTrue(matcher.find(), "V3 迁移脚本至少应包含一个 BCrypt 密文");
        }
    }

    /**
     * 校验 V5 菜单多租户隔离迁移脚本中的 UUID 字面量均为有效 UUID。
     * 入参：无；出参：无；流程：读取 V5 迁移脚本，提取显式 UUID 转换并逐个交给 JDK UUID 解析器校验。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V5 迁移脚本中的 UUID 字面量必须合法")
    void shouldContainOnlyValidUuidLiteralsInV5() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/auth/V5__menu_tenant_isolation_and_visible_status.sql")) {
            assertNotNull(input, "V5 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = UUID_LITERAL.matcher(sql);
            int literalCount = 0;

            while (matcher.find()) {
                String literal = matcher.group(1);
                assertDoesNotThrow(() -> UUID.fromString(literal), "非法 UUID: " + literal);
                literalCount++;
            }

            assertTrue(literalCount > 0, "V5 迁移脚本至少应包含一个 UUID 字面量");
        }
    }

    /**
     * 校验阶段 2-7 所需的新增冒号权限码均由 V6 幂等补齐。
     * 入参：无；出参：无；流程：读取 V6 资源，逐项确认权限码、幂等冲突条件与 UUID 字面量存在。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V6 必须补齐阶段 2-7 所需权限码并保持幂等")
    void shouldContainRequiredStagePermissionsInV6() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/auth/V6__complete_stage_2_7_permissions.sql")) {
            assertNotNull(input, "V6 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<String> requiredPermissions = List.of(
                    "inv:uom:view", "inv:uom:manage",
                    "inv:customer:view", "inv:customer:manage",
                    "inv:supplier:view", "inv:supplier:manage",
                    "inv:location:view", "inv:location:manage",
                    "inv:transfer:view", "inv:transfer:create", "inv:transfer:confirm",
                    "inv:stocktake:view", "inv:stocktake:create", "inv:stocktake:start", "inv:stocktake:confirm",
                    "sales:order:update", "mes:bom:manage", "mes:routing:manage",
                    "iot:device:simulate", "gis:map:manage", "trace:chain:view");

            for (String permission : requiredPermissions) {
                String literal = "'" + permission + "'";
                int occurrenceCount = sql.split(Pattern.quote(literal), -1).length - 1;
                assertEquals(1, occurrenceCount, "V6 权限码必须恰好声明一次: " + permission);
            }
            assertTrue(sql.contains("ON CONFLICT (permission_code) WHERE isdel = 0 DO NOTHING"),
                    "V6 必须按有效权限码冲突保持幂等");
            assertTrue(sql.contains("::uuid"), "V6 权限 ID 必须使用 PostgreSQL UUID 字面量");
        }
    }

    /**
     * 校验 V8 只将默认租户中仍保留旧值的系统种子菜单迁移到正式前端路由。
     * 入参：无；出参：无；流程：读取 V8 资源，逐项确认菜单编码、正式路由、组件路径以及租户和旧值限定均存在。
     *
     * @throws IOException 读取迁移资源失败时抛出
     */
    @Test
    @DisplayName("V8 必须对齐演示菜单的正式前端路由")
    void shouldAlignBuiltinMenuRoutesInV8() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/auth/V8__align_builtin_menu_routes.sql")) {
            assertNotNull(input, "V8 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<String[]> mappings = List.of(
                    new String[]{"master_product", "/master-data/products", "views/master/ProductList.vue", "/master-data?tab=products", "views/masterdata/MasterDataView.vue"},
                    new String[]{"master_warehouse", "/master-data/warehouses", "views/master/WarehouseList.vue", "/master-data?tab=warehouses", "views/masterdata/MasterDataView.vue"},
                    new String[]{"master_inventory", "/master-data/inventory", "views/master/InventoryBalance.vue", "/inventory/balances", "views/inventory/InventoryBalanceView.vue"},
                    new String[]{"purchase_order", "/purchase/orders", "views/purchase/PurchaseOrderList.vue", "/purchasing/orders", "views/purchasing/PurchaseOrderListView.vue"},
                    new String[]{"purchase_inbound", "/purchase/inbound", "views/purchase/PurchaseInbound.vue", "/purchasing/receipts", "views/purchasing/PurchaseOrderListView.vue"},
                    new String[]{"purchase_putaway", "/purchase/putaway", "views/purchase/PutawayTaskList.vue", "/purchasing/putaway", "views/purchasing/PutawayTaskView.vue"},
                    new String[]{"sales_outbound", "/sales/outbound", "views/sales/SalesOutbound.vue", "/sales/picks", "views/sales/PickTaskView.vue"},
                    new String[]{"mes_execution", "/mes/execution", "views/mes/OperationExecution.vue", "/mes/dispatch", "views/manufacturing/DispatchView.vue"},
                    new String[]{"gis", "/gis/map", "views/gis/SiteMap.vue", "/gis/site-maps", "views/insights/SiteMapListView.vue"});

            for (String[] mapping : mappings) {
                assertTrue(sql.contains("menu_code = '" + mapping[0] + "'"),
                        "V8 缺少菜单编码更新: " + mapping[0]);
                assertTrue(sql.contains("route_path = '" + mapping[1] + "'"),
                        "V8 缺少已知旧路由限定: " + mapping[1]);
                assertTrue(sql.contains("component_path = '" + mapping[2] + "'"),
                        "V8 缺少已知旧组件限定: " + mapping[2]);
                assertTrue(sql.contains("SET route_path = '" + mapping[3] + "'"),
                        "V8 缺少正式路由: " + mapping[3]);
                assertTrue(sql.contains("component_path = '" + mapping[4] + "'"),
                        "V8 缺少正式组件路径: " + mapping[4]);
            }
            assertTrue(sql.contains("tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid"),
                    "V8 必须只限定默认租户的系统种子菜单");
            assertTrue(!sql.contains("a0000000-0000-0000-0000-000000000002'::uuid"),
                    "V8 不得更新第二租户的同编码菜单");
        }
    }

    /**
     * 校验 V9 为租户管理员补齐商品与仓库主数据维护权限，且不绑定单一角色 UUID。
     * 入参：无；出参：无；流程：读取 V9 资源，确认角色/权限过滤、有效数据条件和幂等插入均存在。
     *
     * @throws IOException 读取 V9 迁移资源失败时抛出
     */
    @Test
    @DisplayName("V9 必须补齐租户管理员商品与仓库维护权限并保持幂等")
    void shouldGrantTenantAdminMasterdataManagePermissionsInV9() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/auth/V9__grant_tenant_admin_masterdata_manage_permissions.sql")) {
            assertNotNull(input, "V9 迁移脚本必须存在");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertEquals(1, sql.split(Pattern.quote("'inv:product:manage'"), -1).length - 1,
                    "V9 商品维护权限码必须恰好出现一次");
            assertEquals(1, sql.split(Pattern.quote("'inv:warehouse:manage'"), -1).length - 1,
                    "V9 仓库维护权限码必须恰好出现一次");
            assertTrue(sql.contains("r.role_code = 'tenant.admin'"),
                    "V9 必须按租户管理员角色编码授权");
            assertTrue(sql.contains("r.status = 'ACTIVE'") && sql.contains("r.isdel = 0")
                            && sql.contains("p.isdel = 0"),
                    "V9 必须只为有效角色和权限补授权");
            assertTrue(sql.contains("ON CONFLICT DO NOTHING"),
                    "V9 必须保持重复执行幂等");
            assertTrue(!sql.contains("b0000000-0000-0000-0000-000000000001"),
                    "V9 不得绑定单一租户管理员角色 UUID");
        }
    }

}
