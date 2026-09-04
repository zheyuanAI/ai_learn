package com.ailearn.platform.core.purchasing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 采购 V3 迁移静态契约测试，不连接开发数据库。
 */
class PurchasingMigrationScriptTest {

    /**
     * 校验采购订单、到货验收和后续 S3 质量/上架结构均在同一 V3 中，且核心数量约束存在。
     * 入参：无；出参：无；流程：读取迁移文本并检查租户、审计、数量和复合外键片段。
     */
    @Test
    void v3ContainsTenantSafePurchaseAndReceiptFacts() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/core/V3__purchasing_receipt_quality_putaway.sql");
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        String normalized = sql.toLowerCase();

        for (String table : List.of("purchase_order", "purchase_order_line", "purchase_receipt",
                "purchase_receipt_line", "purchase_quality_inspection", "purchase_quality_disposition",
                "putaway_task")) {
            assertTrue(normalized.contains("create table if not exists " + table), "V3 缺少表: " + table);
        }
        assertTrue(normalized.contains("status varchar(32) not null default 'draft'"));
        assertTrue(normalized.contains("arrived_qty numeric(19, 6) not null"));
        assertTrue(normalized.contains("rejected_qty numeric(19, 6) not null"));
        assertTrue(normalized.contains("received_qty numeric(19, 6) not null"));
        assertTrue(normalized.contains("check (arrived_qty = rejected_qty + received_qty)"));
        assertTrue(normalized.contains("rejected_qty = 0 or nullif(btrim(rejection_reason), '') is not null"));
        assertTrue(normalized.contains("completed_session_id varchar(128)"));
        assertTrue(normalized.contains("status = 'draft' and confirmed_by is null"));
        assertTrue(normalized.contains("foreign key (tenant_id, purchase_order_id) references purchase_order"));
        assertTrue(normalized.contains("foreign key (tenant_id, purchase_order_line_id) references purchase_order_line"));
        assertTrue(normalized.contains("uq_purchase_quality_inspection_tenant_id"));
        assertTrue(normalized.contains("where isdel = 0"));
    }
}
