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
 * 预留库位分配 Mapper。
 * <p>
 * 分配移动不删除历史；部分移动通过减少源分配的可分配总量并新增目标分配保留审计轨迹。
 * </p>
 */
@Mapper
public interface InventoryAllocationMapper {

    /**
     * 锁定预留的有效分配，并按分配 ID 稳定排序。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @param allocationId 可选分配 ID
     * @param productId 分配产品
     * @param warehouseId 分配仓库
     * @param locationId 分配库位
     * @param lotNo 分配批次
     * @return 已锁定的有效分配
     */
    @Select("""
            <script>
            SELECT id, tenant_id, reservation_id, product_id, warehouse_id, location_id, lot_no,
                   allocated_qty, released_qty, version, created_at, updated_at
              FROM inv_inventory_reservation_allocation
             WHERE tenant_id = #{tenantId}
               AND reservation_id = #{reservationId}
               AND allocated_qty > released_qty
               AND isdel = 0
            <if test="allocationId != null"> AND id = #{allocationId}</if>
               AND product_id = #{productId}
               AND warehouse_id = #{warehouseId}
               AND location_id = #{locationId}
               AND lot_no = #{lotNo}
             ORDER BY id
             FOR UPDATE
            </script>
            """)
    @Results(id = "allocationRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "reservationId", column = "reservation_id"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "warehouseId", column = "warehouse_id"),
            @Result(property = "locationId", column = "location_id"),
            @Result(property = "lotNo", column = "lot_no"),
            @Result(property = "allocatedQty", column = "allocated_qty"),
            @Result(property = "releasedQty", column = "released_qty"),
            @Result(property = "version", column = "version"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<InventoryAllocationRow> selectActiveForUpdate(@Param("tenantId") UUID tenantId,
                                                       @Param("reservationId") UUID reservationId,
                                                       @Param("allocationId") UUID allocationId,
                                                       @Param("productId") UUID productId,
                                                       @Param("warehouseId") UUID warehouseId,
                                                       @Param("locationId") UUID locationId,
                                                       @Param("lotNo") String lotNo);

    /**
     * 插入一条分配事实。
     *
     * @param row 分配行
     * @param operatorId 操作用户
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO inv_inventory_reservation_allocation
                (id, tenant_id, reservation_id, product_id, warehouse_id, location_id, lot_no,
                 allocated_qty, released_qty, version,
                 created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.reservationId}, #{row.productId}, #{row.warehouseId}, #{row.locationId}, #{row.lotNo},
                 #{row.allocatedQty}, #{row.releasedQty}, #{row.version},
                 #{operatorId}, CURRENT_TIMESTAMP, #{operatorId}, CURRENT_TIMESTAMP, 0)
            """)
    int insert(@Param("row") InventoryAllocationRow row,
               @Param("operatorId") UUID operatorId);

    /**
     * 释放分配数量并递增版本。
     *
     * @param row 已锁定分配
     * @param quantity 释放数量
     * @param operatorId 操作用户
     * @return 更新行数
     */
    @Update("""
            UPDATE inv_inventory_reservation_allocation
               SET released_qty = released_qty + #{quantity},
                   version = version + 1,
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{row.id}
               AND tenant_id = #{row.tenantId}
               AND version = #{row.version}
               AND allocated_qty - released_qty >= #{quantity}
               AND isdel = 0
            """)
    int release(@Param("row") InventoryAllocationRow row,
                @Param("quantity") BigDecimal quantity,
                @Param("operatorId") UUID operatorId);

    /**
     * 将整条有效分配移动到目标维度。
     *
     * @param row 已锁定分配
     * @param productId 目标产品
     * @param warehouseId 目标仓库
     * @param locationId 目标库位
     * @param lotNo 目标批次
     * @param operatorId 操作用户
     * @return 更新行数
     */
    @Update("""
            UPDATE inv_inventory_reservation_allocation
               SET product_id = #{productId},
                   warehouse_id = #{warehouseId},
                   location_id = #{locationId},
                   lot_no = #{lotNo},
                   version = version + 1,
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{row.id}
               AND tenant_id = #{row.tenantId}
               AND version = #{row.version}
               AND allocated_qty - released_qty = #{activeQty}
               AND isdel = 0
            """)
    int moveWhole(@Param("row") InventoryAllocationRow row,
                  @Param("activeQty") BigDecimal activeQty,
                  @Param("productId") UUID productId,
                  @Param("warehouseId") UUID warehouseId,
                  @Param("locationId") UUID locationId,
                  @Param("lotNo") String lotNo,
                  @Param("operatorId") UUID operatorId);

    /**
     * 部分移动时减少源分配的总量，保留其已释放历史和版本。
     *
     * @param row 已锁定源分配
     * @param quantity 部分移动数量
     * @param operatorId 操作用户
     * @return 更新行数
     */
    @Update("""
            UPDATE inv_inventory_reservation_allocation
               SET allocated_qty = allocated_qty - #{quantity},
                   version = version + 1,
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{row.id}
               AND tenant_id = #{row.tenantId}
               AND version = #{row.version}
               AND allocated_qty - released_qty >= #{quantity}
               AND isdel = 0
            """)
    int reduceAllocated(@Param("row") InventoryAllocationRow row,
                        @Param("quantity") BigDecimal quantity,
                        @Param("operatorId") UUID operatorId);

    /**
     * 按预留 ID 查询有效分配，用于查询结果组装。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @return 分配列表
     */
    @Select("""
            SELECT id, tenant_id, reservation_id, product_id, warehouse_id, location_id, lot_no,
                   allocated_qty, released_qty, version, created_at, updated_at
              FROM inv_inventory_reservation_allocation
             WHERE tenant_id = #{tenantId}
               AND reservation_id = #{reservationId}
               AND isdel = 0
             ORDER BY id
            """)
    @ResultMap("allocationRowMap")
    List<InventoryAllocationRow> selectByReservationId(@Param("tenantId") UUID tenantId,
                                                       @Param("reservationId") UUID reservationId);
}
