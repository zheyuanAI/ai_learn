package com.ailearn.platform.core.manufacturing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V5 迁移静态契约测试；检查 foundation 持久化与执行状态所需的增量边界。 */
class ManufacturingExecutionMigrationScriptTest {

    /**
     * 验证 V5 创建 foundation 基础表，且执行表携带租户、工单软引用、状态和库存事实关联字段。
     * 入参：无；出参：无；流程：读取迁移文本并检查关键 DDL 片段，不连接任何数据库。
     */
    @Test
    void v5AddsExecutionLinksWithoutRecreatingFoundation() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/core/V5__manufacturing_execution_inventory_links.sql");
        String normalized = Files.readString(path, StandardCharsets.UTF_8).toLowerCase();

        for (String table : List.of("mes_dispatch_order", "mes_operation_execution", "mes_work_report",
                "mes_quality_inspection", "mes_material_issue", "mes_material_return",
                "mes_finished_goods_receipt")) {
            assertTrue(normalized.contains("create table if not exists " + table), "V5 缺少执行表: " + table);
        }
        assertTrue(normalized.contains("work_order_id uuid not null"));
        assertTrue(normalized.contains("status varchar(32) not null default 'notstarted'"));
        assertTrue(normalized.contains("inventory_transaction_id uuid"));
        assertTrue(normalized.contains("idempotency_key varchar(128)"));
        assertTrue(normalized.contains("create table if not exists mes_bom"));
        assertTrue(normalized.contains("create table if not exists mes_routing"));
        assertTrue(normalized.contains("create table if not exists mes_work_order"));
        assertTrue(normalized.contains("foreign key (tenant_id, bom_id) references mes_bom"));
        assertTrue(normalized.contains("foreign key (tenant_id, routing_id) references mes_routing"));
        assertFalse(normalized.contains("references inv_inventory_balance"));
    }
}
