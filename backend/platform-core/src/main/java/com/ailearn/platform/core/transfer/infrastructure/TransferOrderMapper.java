package com.ailearn.platform.core.transfer.infrastructure;

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
 * 调拨单及明细 MyBatis Mapper；不调用库存表。
 */
@Mapper
public interface TransferOrderMapper {

    /**
     * 按租户读取调拨表头。
     */
    @Select("""
            SELECT id, tenant_id, transfer_no, from_warehouse_id, from_location_id,
                   to_warehouse_id, to_location_id, status, version, confirmed_by, confirmed_at,
                   created_by, created_at, updated_by, updated_at
              FROM inv_transfer_order
             WHERE tenant_id = #{tenantId} AND id = #{id} AND isdel = 0
             LIMIT 1
            """)
    @Results(id = "transferOrderRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "transferNo", column = "transfer_no"),
            @Result(property = "fromWarehouseId", column = "from_warehouse_id"),
            @Result(property = "fromLocationId", column = "from_location_id"),
            @Result(property = "toWarehouseId", column = "to_warehouse_id"),
            @Result(property = "toLocationId", column = "to_location_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "version", column = "version"),
            @Result(property = "confirmedBy", column = "confirmed_by"),
            @Result(property = "confirmedAt", column = "confirmed_at"),
            @Result(property = "createdBy", column = "created_by"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedBy", column = "updated_by"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    TransferOrderRow findById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * 按租户读取调拨明细。
     */
    @Select("""
            SELECT id, tenant_id, transfer_order_id, line_no, product_id, lot_no, uom, quantity
              FROM inv_transfer_order_line
             WHERE tenant_id = #{tenantId} AND transfer_order_id = #{orderId} AND isdel = 0
             ORDER BY line_no, id
            """)
    @Results(id = "transferLineRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "transferOrderId", column = "transfer_order_id"),
            @Result(property = "lineNo", column = "line_no"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "lotNo", column = "lot_no"),
            @Result(property = "uom", column = "uom"),
            @Result(property = "quantity", column = "quantity")
    })
    List<TransferLineRow> findLines(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

    /**
     * 插入调拨表头。
     */
    @Insert("""
            INSERT INTO inv_transfer_order
                (id, tenant_id, transfer_no, from_warehouse_id, from_location_id,
                 to_warehouse_id, to_location_id, status, version, created_by, created_at,
                 updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.transferNo}, #{row.fromWarehouseId}, #{row.fromLocationId},
                 #{row.toWarehouseId}, #{row.toLocationId}, #{row.status}, #{row.version}, #{row.createdBy},
                 #{row.createdAt}, #{row.updatedBy}, #{row.updatedAt}, 0)
            """)
    int insertOrder(@Param("row") TransferOrderRow row);

    /**
     * 插入调拨明细。
     */
    @Insert("""
            INSERT INTO inv_transfer_order_line
                (id, tenant_id, transfer_order_id, line_no, product_id, lot_no, uom, quantity,
                 created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.transferOrderId}, #{row.lineNo}, #{row.productId},
                 #{row.lotNo}, #{row.uom}, #{row.quantity}, #{operatorId}, CURRENT_TIMESTAMP,
                 #{operatorId}, CURRENT_TIMESTAMP, 0)
            """)
    int insertLine(@Param("row") TransferLineRow row, @Param("operatorId") UUID operatorId);

    /**
     * 以版本条件确认调拨表头。
     */
    @Update("""
            UPDATE inv_transfer_order
               SET status = 'Confirmed', version = version + 1,
                   confirmed_by = #{operatorId}, confirmed_at = #{confirmedAt},
                   updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'Draft'
               AND version = #{expectedVersion} AND isdel = 0
            """)
    int confirm(@Param("tenantId") UUID tenantId,
                @Param("id") UUID id,
                @Param("expectedVersion") long expectedVersion,
                @Param("operatorId") UUID operatorId,
                @Param("confirmedAt") OffsetDateTime confirmedAt);
}
