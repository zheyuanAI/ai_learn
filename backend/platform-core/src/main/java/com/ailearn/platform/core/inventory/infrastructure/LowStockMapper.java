package com.ailearn.platform.core.inventory.infrastructure;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 低库存只读聚合 Mapper；所有查询必须显式携带可信 tenant_id。 */
@Mapper
public interface LowStockMapper {

    /** 在数据库侧聚合可用量并按缺口倒序返回，额外读取一条用于判断截断。 */
    @Select("""
            <script>
            SELECT p.id AS product_id,
                   p.sku,
                   p.name AS product_name,
                   p.uom,
                   p.safety_stock,
                   COALESCE(SUM(b.on_hand_qty - b.reserved_qty), 0) AS available_qty,
                   p.safety_stock - COALESCE(SUM(b.on_hand_qty - b.reserved_qty), 0) AS shortfall_qty,
                   MAX(b.last_transaction_at) AS last_transaction_at
              FROM md_product p
              LEFT JOIN inv_inventory_balance b
                ON b.tenant_id = p.tenant_id
               AND b.product_id = p.id
               AND b.isdel = 0
            <if test="warehouseId != null"> AND b.warehouse_id = #{warehouseId}</if>
             WHERE p.tenant_id = #{tenantId}
               AND p.isdel = 0
               AND p.status = 'ACTIVE'
               AND p.safety_stock IS NOT NULL
             GROUP BY p.id, p.sku, p.name, p.uom, p.safety_stock
            HAVING COALESCE(SUM(b.on_hand_qty - b.reserved_qty), 0) &lt; p.safety_stock
             ORDER BY shortfall_qty DESC, p.sku, p.id
             LIMIT #{fetchLimit}
            </script>
            """)
    List<LowStockRow> selectLowStock(@Param("tenantId") UUID tenantId,
                                     @Param("warehouseId") UUID warehouseId,
                                     @Param("fetchLimit") int fetchLimit);
}
