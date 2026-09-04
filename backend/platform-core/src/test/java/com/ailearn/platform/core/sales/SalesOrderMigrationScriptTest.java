package com.ailearn.platform.core.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 销售订单 V4 迁移静态约束测试，不连接任何数据库。
 */
class SalesOrderMigrationScriptTest {

    /**
     * 确认迁移保存双轴所需持久化字段和订单行数量链约束。
     *
     * @throws Exception 迁移文件读取失败
     */
    @Test
    void v4ContainsDualAxisAndQuantityFacts() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/core/V4__sales_reservation_pick_shipment.sql");
        String sql = Files.readString(path, StandardCharsets.UTF_8);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS sales_order"));
        assertTrue(sql.contains("status IN ('Draft', 'Submitted', 'Approved', 'Completed')"));
        assertTrue(sql.contains("completion_type"));
        assertTrue(sql.contains("completed_session_id"));
        assertTrue(sql.contains("ordered_qty NUMERIC(19, 6)"));
        assertTrue(sql.contains("reserved_qty NUMERIC(19, 6)"));
        assertTrue(sql.contains("picked_qty NUMERIC(19, 6)"));
        assertTrue(sql.contains("shipped_qty NUMERIC(19, 6)"));
        assertTrue(sql.contains("shipped_qty <= picked_qty AND picked_qty <= reserved_qty"));
        assertTrue(!sql.contains("fulfillment_status"), "派生履约状态不得落库");
    }
}
