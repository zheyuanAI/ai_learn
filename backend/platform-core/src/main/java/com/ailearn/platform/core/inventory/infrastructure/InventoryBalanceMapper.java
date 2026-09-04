package com.ailearn.platform.core.inventory.infrastructure;

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
 * 库存余额 Mapper。
 * <p>
 * 所有读取和更新都显式带 {@code tenant_id}；写入使用维度唯一键幂等插入，更新使用
 * {@code version} 条件并在需要时由 Repository 先执行 {@code FOR UPDATE}。
 * </p>
 */
@Mapper
public interface InventoryBalanceMapper {

    /**
     * 按完整库存维度锁定余额行。
     *
     * @param tenantId 租户 ID
     * @param productId 产品 ID
     * @param warehouseId 仓库 ID
     * @param locationId 库位 ID
     * @param lotNo 规范化批次号
     * @return 锁定的余额行，不存在时返回 null
     */
    @Select("""
            SELECT id, tenant_id, product_id, warehouse_id, location_id, lot_no,
                   on_hand_qty, reserved_qty, version, last_transaction_at
              FROM inv_inventory_balance
             WHERE tenant_id = #{tenantId}
               AND product_id = #{productId}
               AND warehouse_id = #{warehouseId}
               AND location_id = #{locationId}
               AND lot_no = #{lotNo}
               AND isdel = 0
             LIMIT 1
             FOR UPDATE
            """)
    @Results(id = "balanceRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "productId", column = "product_id"),
            @Result(property = "warehouseId", column = "warehouse_id"),
            @Result(property = "locationId", column = "location_id"),
            @Result(property = "lotNo", column = "lot_no"),
            @Result(property = "onHandQty", column = "on_hand_qty"),
            @Result(property = "reservedQty", column = "reserved_qty"),
            @Result(property = "version", column = "version"),
            @Result(property = "lastTransactionAt", column = "last_transaction_at")
    })
    InventoryBalanceRow selectForUpdate(@Param("tenantId") UUID tenantId,
                                        @Param("productId") UUID productId,
                                        @Param("warehouseId") UUID warehouseId,
                                        @Param("locationId") UUID locationId,
                                        @Param("lotNo") String lotNo);

    /**
     * 原子创建零余额行；并发冲突时保留已存在行。
     *
     * @param row 待创建余额行
     * @return 插入成功为 1，已存在为 0
     */
    @Insert("""
            INSERT INTO inv_inventory_balance
                (id, tenant_id, product_id, warehouse_id, location_id, lot_no,
                 on_hand_qty, reserved_qty, version, last_transaction_at,
                 created_by, created_at, updated_by, updated_at, isdel)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.productId}, #{row.warehouseId}, #{row.locationId}, #{row.lotNo},
                 #{row.onHandQty}, #{row.reservedQty}, #{row.version}, #{row.lastTransactionAt},
                 #{operatorId}, CURRENT_TIMESTAMP, #{operatorId}, CURRENT_TIMESTAMP, 0)
            ON CONFLICT (tenant_id, product_id, warehouse_id, location_id, lot_no)
                WHERE isdel = 0 DO NOTHING
            """)
    int insertIfAbsent(@Param("row") InventoryBalanceRow row,
                       @Param("operatorId") UUID operatorId);

    /**
     * 以版本条件原子更新实物和预留，防止余额被并发覆盖。
     *
     * @param row 已锁定且携带期望版本的余额行
     * @param operatorId 操作用户
     * @return 更新行数
     */
    @Update("""
            UPDATE inv_inventory_balance
               SET on_hand_qty = #{row.onHandQty},
                   reserved_qty = #{row.reservedQty},
                   version = version + 1,
                   last_transaction_at = #{row.lastTransactionAt},
                   updated_by = #{operatorId},
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{row.id}
               AND tenant_id = #{row.tenantId}
               AND version = #{row.version}
               AND isdel = 0
            """)
    int updateAmounts(@Param("row") InventoryBalanceRow row,
                      @Param("operatorId") UUID operatorId);

    /**
     * 按租户和筛选条件分页查询余额。
     *
     * @param tenantId 可信租户
     * @param productId 可选产品
     * @param warehouseId 可选仓库
     * @param locationId 可选库位
     * @param lotNo 可选批次
     * @param limit 页大小
     * @param offset 页偏移
     * @return 当前页余额行
     */
    @Select("""
            <script>
            SELECT id, tenant_id, product_id, warehouse_id, location_id, lot_no,
                   on_hand_qty, reserved_qty, version, last_transaction_at
              FROM inv_inventory_balance
             WHERE tenant_id = #{tenantId}
               AND isdel = 0
            <if test="productId != null"> AND product_id = #{productId}</if>
            <if test="warehouseId != null"> AND warehouse_id = #{warehouseId}</if>
            <if test="locationId != null"> AND location_id = #{locationId}</if>
            <if test="lotNo != null"> AND lot_no = #{lotNo}</if>
             ORDER BY product_id, warehouse_id, location_id, lot_no, id
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @ResultMap("balanceRowMap")
    List<InventoryBalanceRow> selectPage(@Param("tenantId") UUID tenantId,
                                         @Param("productId") UUID productId,
                                         @Param("warehouseId") UUID warehouseId,
                                         @Param("locationId") UUID locationId,
                                         @Param("lotNo") String lotNo,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    /**
     * 统计余额查询总数。
     *
     * @param tenantId 可信租户
     * @param productId 可选产品
     * @param warehouseId 可选仓库
     * @param locationId 可选库位
     * @param lotNo 可选批次
     * @return 总条数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
              FROM inv_inventory_balance
             WHERE tenant_id = #{tenantId}
               AND isdel = 0
            <if test="productId != null"> AND product_id = #{productId}</if>
            <if test="warehouseId != null"> AND warehouse_id = #{warehouseId}</if>
            <if test="locationId != null"> AND location_id = #{locationId}</if>
            <if test="lotNo != null"> AND lot_no = #{lotNo}</if>
            </script>
            """)
    long count(@Param("tenantId") UUID tenantId,
               @Param("productId") UUID productId,
               @Param("warehouseId") UUID warehouseId,
               @Param("locationId") UUID locationId,
               @Param("lotNo") String lotNo);

    /**
     * 聚合租户内指定库位的当前实物和有效预留数量。
     *
     * @param tenantId 可信租户
     * @param locationId 库位 ID
     * @return 库位使用量聚合行
     */
    @Select("""
            SELECT COALESCE(SUM(on_hand_qty), 0) AS on_hand_qty,
                   COALESCE(SUM(reserved_qty), 0) AS reserved_qty
              FROM inv_inventory_balance
             WHERE tenant_id = #{tenantId}
               AND location_id = #{locationId}
               AND isdel = 0
            """)
    @Results(id = "locationUsageRowMap", value = {
            @Result(property = "onHandQty", column = "on_hand_qty"),
            @Result(property = "reservedQty", column = "reserved_qty")
    })
    InventoryLocationUsageRow selectUsageByLocation(@Param("tenantId") UUID tenantId,
                                                    @Param("locationId") UUID locationId);

}
