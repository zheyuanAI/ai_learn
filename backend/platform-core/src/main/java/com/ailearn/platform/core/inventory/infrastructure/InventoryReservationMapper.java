package com.ailearn.platform.core.inventory.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 库存预留 Mapper，所有写入都保留原始预留事实，不物理删除。
 */
@Mapper
public interface InventoryReservationMapper {

    /**
     * 按租户锁定预留。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @return 锁定预留或 null
     */
    @Select("""
            SELECT id, tenant_id, reservation_no, source_type, source_id, source_line_id,
                   reserved_qty, released_qty, status, version, created_at, updated_at
              FROM inv_inventory_reservation
             WHERE tenant_id = #{tenantId}
               AND id = #{reservationId}
               AND isdel = 0
             LIMIT 1
             FOR UPDATE
            """)
    @Results(id = "reservationRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "reservationNo", column = "reservation_no"),
            @Result(property = "sourceType", column = "source_type"),
            @Result(property = "sourceId", column = "source_id"),
            @Result(property = "sourceLineId", column = "source_line_id"),
            @Result(property = "reservedQty", column = "reserved_qty"),
            @Result(property = "releasedQty", column = "released_qty"),
            @Result(property = "status", column = "status"),
            @Result(property = "version", column = "version"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    InventoryReservationRow selectForUpdate(@Param("tenantId") UUID tenantId,
                                             @Param("reservationId") UUID reservationId);

    /**
     * 插入新预留。
     *
     * @param row 预留行
     * @param operatorId 操作用户
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO inv_inventory_reservation
                (id, tenant_id, reservation_no, source_type, source_id, source_line_id,
                 reserved_qty, released_qty, status, version,
                 created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.reservationNo}, #{row.sourceType}, #{row.sourceId}, #{row.sourceLineId},
                 #{row.reservedQty}, #{row.releasedQty}, #{row.status}, #{row.version},
                 #{operatorId}, CURRENT_TIMESTAMP, #{operatorId}, CURRENT_TIMESTAMP, 0)
            """)
    int insert(@Param("row") InventoryReservationRow row,
               @Param("operatorId") UUID operatorId);

    /**
     * 以版本条件更新预留释放量与状态。
     *
     * @param row 已锁定预留行
     * @param quantity 本次释放量
     * @param operatorId 操作用户
     * @return 更新行数
     */
    @Update("""
            UPDATE inv_inventory_reservation
               SET released_qty = released_qty + #{quantity},
                   status = CASE
                       WHEN released_qty + #{quantity} >= reserved_qty THEN 'Released'
                       ELSE 'PartiallyReleased'
                   END,
                   version = version + 1,
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{row.id}
               AND tenant_id = #{row.tenantId}
               AND version = #{row.version}
               AND released_qty + #{quantity} <= reserved_qty
               AND isdel = 0
            """)
    int release(@Param("row") InventoryReservationRow row,
                @Param("quantity") BigDecimal quantity,
                @Param("operatorId") UUID operatorId);

    /**
     * 按租户和来源条件分页查询预留。
     *
     * @param tenantId 可信租户
     * @param reservationId 可选预留 ID
     * @param sourceType 可选来源类型
     * @param sourceId 可选来源单据
     * @param sourceLineId 可选来源明细
     * @param status 可选状态
     * @param limit 页大小
     * @param offset 页偏移
     * @return 当前页预留
     */
    @Select("""
            <script>
            SELECT r.id, r.tenant_id, r.reservation_no, r.source_type, r.source_id, r.source_line_id,
                   r.reserved_qty, r.released_qty, r.status, r.version, r.created_at, r.updated_at
              FROM inv_inventory_reservation r
             WHERE r.tenant_id = #{tenantId}
               AND r.isdel = 0
            <if test="reservationId != null"> AND r.id = #{reservationId}</if>
            <if test="sourceType != null and sourceType != ''"> AND r.source_type = #{sourceType}</if>
            <if test="sourceId != null"> AND r.source_id = #{sourceId}</if>
            <if test="sourceLineId != null"> AND r.source_line_id = #{sourceLineId}</if>
            <if test="status != null and status != ''"> AND r.status = #{status}</if>
            <if test="productId != null or warehouseId != null or locationId != null or lotNo != null">
               AND EXISTS (
                   SELECT 1
                     FROM inv_inventory_reservation_allocation a
                    WHERE a.tenant_id = r.tenant_id
                      AND a.reservation_id = r.id
                      AND a.isdel = 0
            <if test="productId != null"> AND a.product_id = #{productId}</if>
            <if test="warehouseId != null"> AND a.warehouse_id = #{warehouseId}</if>
            <if test="locationId != null"> AND a.location_id = #{locationId}</if>
            <if test="lotNo != null"> AND a.lot_no = #{lotNo}</if>
               )
            </if>
             ORDER BY r.created_at DESC, r.id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("reservationRowMap")
    List<InventoryReservationRow> selectPage(@Param("tenantId") UUID tenantId,
                                             @Param("reservationId") UUID reservationId,
                                             @Param("sourceType") String sourceType,
                                             @Param("sourceId") UUID sourceId,
                                             @Param("sourceLineId") UUID sourceLineId,
                                             @Param("status") String status,
                                             @Param("productId") UUID productId,
                                             @Param("warehouseId") UUID warehouseId,
                                             @Param("locationId") UUID locationId,
                                             @Param("lotNo") String lotNo,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    /**
     * 统计预留总数。
     *
     * @param tenantId 可信租户
     * @param reservationId 可选预留 ID
     * @param sourceType 可选来源类型
     * @param sourceId 可选来源单据
     * @param sourceLineId 可选来源明细
     * @param status 可选状态
     * @return 总条数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
              FROM inv_inventory_reservation r
             WHERE r.tenant_id = #{tenantId}
               AND r.isdel = 0
            <if test="reservationId != null"> AND r.id = #{reservationId}</if>
            <if test="sourceType != null and sourceType != ''"> AND r.source_type = #{sourceType}</if>
            <if test="sourceId != null"> AND r.source_id = #{sourceId}</if>
            <if test="sourceLineId != null"> AND r.source_line_id = #{sourceLineId}</if>
            <if test="status != null and status != ''"> AND r.status = #{status}</if>
            <if test="productId != null or warehouseId != null or locationId != null or lotNo != null">
               AND EXISTS (
                   SELECT 1
                     FROM inv_inventory_reservation_allocation a
                    WHERE a.tenant_id = r.tenant_id
                      AND a.reservation_id = r.id
                      AND a.isdel = 0
            <if test="productId != null"> AND a.product_id = #{productId}</if>
            <if test="warehouseId != null"> AND a.warehouse_id = #{warehouseId}</if>
            <if test="locationId != null"> AND a.location_id = #{locationId}</if>
            <if test="lotNo != null"> AND a.lot_no = #{lotNo}</if>
               )
            </if>
            </script>
            """)
    long count(@Param("tenantId") UUID tenantId,
               @Param("reservationId") UUID reservationId,
               @Param("sourceType") String sourceType,
               @Param("sourceId") UUID sourceId,
               @Param("sourceLineId") UUID sourceLineId,
               @Param("status") String status,
               @Param("productId") UUID productId,
               @Param("warehouseId") UUID warehouseId,
               @Param("locationId") UUID locationId,
               @Param("lotNo") String lotNo);

}
