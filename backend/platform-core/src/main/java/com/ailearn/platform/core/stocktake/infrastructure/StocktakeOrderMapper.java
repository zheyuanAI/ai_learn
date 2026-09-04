package com.ailearn.platform.core.stocktake.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 盘点单及明细 MyBatis Mapper，所有查询均带租户和逻辑删除条件。
 */
@Mapper
public interface StocktakeOrderMapper {

    /**
     * 按租户读取盘点表头。
     */
    @Select("""
            SELECT id, tenant_id, stocktake_no, warehouse_id, location_id, status, version,
                   started_by, started_at, confirmed_by, confirmed_at, created_by, created_at,
                   updated_by, updated_at
              FROM inv_stocktake_order
             WHERE tenant_id = #{tenantId} AND id = #{id} AND isdel = 0
             LIMIT 1
            """)
    @Results(id = "stocktakeOrderRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "stocktakeNo", column = "stocktake_no"),
            @Result(property = "warehouseId", column = "warehouse_id"),
            @Result(property = "locationId", column = "location_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "version", column = "version"),
            @Result(property = "startedBy", column = "started_by"),
            @Result(property = "startedAt", column = "started_at"),
            @Result(property = "confirmedBy", column = "confirmed_by"),
            @Result(property = "confirmedAt", column = "confirmed_at"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedBy", column = "updated_by"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    StocktakeOrderRow findById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * 按租户读取盘点系统快照明细。
     */
    @Select("""
            SELECT id, tenant_id, stocktake_order_id, line_no, product_id, warehouse_id,
                   location_id, lot_no, system_qty, system_balance_version, counted_qty,
                   variance_reason, adjustment_transaction_id, created_by, created_at,
                   updated_by, updated_at
              FROM inv_stocktake_order_line
             WHERE tenant_id = #{tenantId} AND stocktake_order_id = #{orderId} AND isdel = 0
             ORDER BY line_no, id
            """)
    @Results(id = "stocktakeLineRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "stocktakeOrderId", column = "stocktake_order_id"),
            @Result(property = "lineNo", column = "line_no"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "warehouseId", column = "warehouse_id"),
            @Result(property = "locationId", column = "location_id"),
            @Result(property = "lotNo", column = "lot_no"),
            @Result(property = "systemQty", column = "system_qty"),
            @Result(property = "systemBalanceVersion", column = "system_balance_version"),
            @Result(property = "countedQty", column = "counted_qty"),
            @Result(property = "varianceReason", column = "variance_reason"),
            @Result(property = "adjustmentTransactionId", column = "adjustment_transaction_id"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedBy", column = "updated_by"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<StocktakeLineRow> findLines(@Param("tenantId") UUID tenantId,
                                     @Param("orderId") UUID orderId);

    /**
     * 插入盘点表头。
     */
    @Insert("""
            INSERT INTO inv_stocktake_order
                (id, tenant_id, stocktake_no, warehouse_id, location_id, status, version,
                 created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.stocktakeNo}, #{row.warehouseId}, #{row.locationId},
                 #{row.status}, #{row.version}, #{row.createdBy}, #{row.createdAt},
                 #{row.updatedBy}, #{row.updatedAt}, 0)
            """)
    int insertOrder(@Param("row") StocktakeOrderRow row);

    /**
     * 插入一条系统快照明细。
     */
    @Insert("""
            INSERT INTO inv_stocktake_order_line
                (id, tenant_id, stocktake_order_id, line_no, product_id, warehouse_id, location_id,
                 lot_no, system_qty, system_balance_version, counted_qty, variance_reason,
                 adjustment_transaction_id, created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.stocktakeOrderId}, #{row.lineNo}, #{row.productId},
                 #{row.warehouseId}, #{row.locationId}, #{row.lotNo}, #{row.systemQty},
                 #{row.systemBalanceVersion}, #{row.countedQty}, #{row.varianceReason},
                 #{row.adjustmentTransactionId}, #{operatorId}, CURRENT_TIMESTAMP,
                 #{operatorId}, CURRENT_TIMESTAMP, 0)
            """)
    int insertLine(@Param("row") StocktakeLineRow row, @Param("operatorId") UUID operatorId);

    /**
     * 以版本条件把盘点单推进到 Counting。
     */
    @Update("""
            UPDATE inv_stocktake_order
               SET status = 'Counting', version = version + 1,
                   started_by = #{operatorId}, started_at = #{startedAt},
                   updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'NotStarted'
               AND version = #{expectedVersion} AND isdel = 0
            """)
    int start(@Param("tenantId") UUID tenantId,
              @Param("id") UUID id,
              @Param("expectedVersion") long expectedVersion,
              @Param("operatorId") UUID operatorId,
              @Param("startedAt") OffsetDateTime startedAt);

    /**
     * 写入一条实盘数量、差异原因及调整流水引用。
     */
    @Update("""
            UPDATE inv_stocktake_order_line
               SET counted_qty = #{row.countedQty}, variance_reason = #{row.varianceReason},
                   adjustment_transaction_id = #{row.adjustmentTransactionId},
                   updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId} AND stocktake_order_id = #{orderId}
               AND id = #{row.id} AND isdel = 0
            """)
    int updateLine(@Param("tenantId") UUID tenantId,
                   @Param("orderId") UUID orderId,
                   @Param("row") StocktakeLineRow row,
                   @Param("operatorId") UUID operatorId);

    /**
     * 以版本条件把盘点单推进到 ConfirmedAdjusted。
     */
    @Update("""
            UPDATE inv_stocktake_order
               SET status = 'ConfirmedAdjusted', version = version + 1,
                   confirmed_by = #{operatorId}, confirmed_at = #{confirmedAt},
                   updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'Counting'
               AND version = #{expectedVersion} AND isdel = 0
            """)
    int confirm(@Param("tenantId") UUID tenantId,
                @Param("id") UUID id,
                @Param("expectedVersion") long expectedVersion,
                @Param("operatorId") UUID operatorId,
                @Param("confirmedAt") OffsetDateTime confirmedAt);
}
