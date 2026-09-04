package com.ailearn.platform.core.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core V2 迁移脚本静态契约检查，不连接数据库。
 */
class CoreMigrationScriptTest {

    private static final List<String> REQUIRED_TABLES = List.of(
            "md_uom", "md_product", "md_customer", "md_supplier", "md_warehouse", "md_location",
            "inv_inventory_balance", "inv_inventory_reservation", "inv_inventory_reservation_allocation",
            "inv_inventory_transaction", "inv_transfer_order", "inv_transfer_order_line",
            "inv_stocktake_order", "inv_stocktake_order_line", "core_idempotency_record");

    /**
     * 校验 V2 迁移包含阶段 2 的全部表、租户字段、数量类型和库存不变量。
     * 入参：无；出参：无；流程：读取 classpath SQL 并检查冻结的结构片段。
     *
     * @throws IOException 读取迁移资源失败
     */
    @Test
    void shouldContainStageTwoTablesAndInventoryConstraints() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/core/V2__master_data_inventory_transfer_stocktake.sql")) {
            assertTrue(input != null, "Core V2 迁移脚本必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String normalized = sql.toLowerCase();
        String compact = normalized.replaceAll("\\s+", " ");
        for (String table : REQUIRED_TABLES) {
            assertTrue(normalized.contains("create table if not exists " + table),
                    "V2 缺少表: " + table);
        }
        assertTrue(count(normalized, "tenant_id uuid not null") >= REQUIRED_TABLES.size(),
                "所有 Core V2 业务表都必须包含非空 tenant_id");
        assertTrue(normalized.contains("on_hand_qty numeric(19, 6)"),
                "库存实物数量必须是 NUMERIC(19,6)");
        assertTrue(normalized.contains("reserved_qty numeric(19, 6)"),
                "库存预留数量必须是 NUMERIC(19,6)");
        assertTrue(normalized.contains("on_hand_qty >= 0")
                        && normalized.contains("reserved_qty >= 0")
                        && normalized.contains("reserved_qty <= on_hand_qty"),
                "库存余额必须声明非负及预留不超过实物约束");
        assertTrue(normalized.contains("where isdel = 0"),
                "租户内有效编码唯一索引必须排除逻辑删除数据");
        assertTrue(compact.contains("type in ('qualityhold', 'receivingstaging', 'storage', 'picking', "
                        + "'shippingstaging', 'adjustment')"),
                "库位类型必须保持一期白名单");
        assertTrue(normalized.contains("claim_token uuid not null"),
                "幂等记录必须保存不可伪造的 claim token");
        assertTrue(normalized.contains("foreign key (tenant_id, warehouse_id) references md_warehouse (tenant_id, id)"),
                "库存主数据引用必须包含租户一致性外键");
        assertTrue(normalized.contains("foreign key (tenant_id, transfer_order_id) references inv_transfer_order (tenant_id, id)"),
                "调拨明细必须通过复合外键绑定当前租户调拨单");
    }

    private static long count(String value, String fragment) {
        return Pattern.compile(Pattern.quote(fragment)).matcher(value).results().count();
    }
}
