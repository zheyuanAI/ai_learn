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

/**
 * 库存只追加流水 Mapper。
 * <p>
 * 接口只暴露 INSERT 和 SELECT，不提供 UPDATE/DELETE，避免把库存事实当作可覆盖快照。
 * </p>
 */
@Mapper
public interface InventoryTransactionMapper {

    /**
     * 追加一条库存事实流水。
     *
     * @param row 待追加流水
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO inv_inventory_transaction
                (id, tenant_id, transaction_no, transaction_type, source_type, source_id, source_line_id,
                 from_product_id, from_warehouse_id, from_location_id, from_lot_no,
                 to_product_id, to_warehouse_id, to_location_id, to_lot_no,
                 quantity, occurred_at, operator_id, session_id, request_id, idempotency_key, payload_digest, created_by, created_at)
            VALUES
                (#{row.id}, #{row.tenantId}, #{row.transactionNo}, #{row.transactionType}, #{row.sourceType}, #{row.sourceId}, #{row.sourceLineId},
                 #{row.fromProductId}, #{row.fromWarehouseId}, #{row.fromLocationId}, #{row.fromLotNo},
                 #{row.toProductId}, #{row.toWarehouseId}, #{row.toLocationId}, #{row.toLotNo},
                 #{row.quantity}, #{row.occurredAt}, #{row.operatorId}, #{row.sessionId}, #{row.requestId}, #{row.idempotencyKey}, #{row.payloadDigest}, #{row.operatorId}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("row") InventoryTransactionRow row);

    /**
     * 按租户和来源/维度条件分页查询流水。
     *
     * @param tenantId 可信租户
     * @param transactionType 可选交易类型
     * @param sourceType 可选来源类型
     * @param sourceId 可选来源 ID
     * @param sourceLineId 可选来源明细 ID
     * @param productId 可选产品
     * @param warehouseId 可选仓库（匹配来源或目标仓库）
     * @param locationId 可选库位（匹配来源或目标库位）
     * @param lotNo 可选批次
     * @param occurredFrom 可选时间起点
     * @param occurredTo 可选时间终点
     * @param limit 页大小
     * @param offset 页偏移
     * @return 当前页流水
     */
    @Select("""
            <script>
            SELECT id, tenant_id, transaction_no, transaction_type, source_type, source_id, source_line_id,
                   from_product_id, from_warehouse_id, from_location_id, from_lot_no,
                   to_product_id, to_warehouse_id, to_location_id, to_lot_no,
                   quantity, occurred_at, operator_id, session_id, request_id, idempotency_key, payload_digest, created_at
             FROM inv_inventory_transaction
             WHERE tenant_id = #{tenantId}
               AND isdel = 0
            <if test="transactionType != null and transactionType != ''"> AND transaction_type = #{transactionType}</if>
            <if test="sourceType != null and sourceType != ''"> AND source_type = #{sourceType}</if>
            <if test="sourceId != null"> AND source_id = #{sourceId}</if>
            <if test="sourceLineId != null"> AND source_line_id = #{sourceLineId}</if>
            <if test="productId != null"> AND (from_product_id = #{productId} OR to_product_id = #{productId})</if>
            <if test="warehouseId != null"> AND (from_warehouse_id = #{warehouseId} OR to_warehouse_id = #{warehouseId})</if>
            <if test="locationId != null"> AND (from_location_id = #{locationId} OR to_location_id = #{locationId})</if>
            <if test="lotNo != null"> AND (from_lot_no = #{lotNo} OR to_lot_no = #{lotNo})</if>
            <if test="occurredFrom != null"> AND occurred_at &gt;= #{occurredFrom}</if>
            <if test="occurredTo != null"> AND occurred_at &lt;= #{occurredTo}</if>
             ORDER BY occurred_at DESC, id DESC
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    @Results(id = "transactionRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "transactionNo", column = "transaction_no"),
            @Result(property = "transactionType", column = "transaction_type"),
            @Result(property = "sourceType", column = "source_type"),
            @Result(property = "sourceId", column = "source_id"),
            @Result(property = "sourceLineId", column = "source_line_id"),
            @Result(property = "fromProductId", column = "from_product_id"),
            @Result(property = "fromWarehouseId", column = "from_warehouse_id"),
            @Result(property = "fromLocationId", column = "from_location_id"),
            @Result(property = "fromLotNo", column = "from_lot_no"),
            @Result(property = "toProductId", column = "to_product_id"),
            @Result(property = "toWarehouseId", column = "to_warehouse_id"),
            @Result(property = "toLocationId", column = "to_location_id"),
            @Result(property = "toLotNo", column = "to_lot_no"),
            @Result(property = "quantity", column = "quantity"),
            @Result(property = "occurredAt", column = "occurred_at"),
            @Result(property = "operatorId", column = "operator_id"),
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "requestId", column = "request_id"),
            @Result(property = "idempotencyKey", column = "idempotency_key"),
            @Result(property = "payloadDigest", column = "payload_digest"),
            @Result(property = "createdAt", column = "created_at")
    })
    List<InventoryTransactionRow> selectPage(@Param("tenantId") UUID tenantId,
                                             @Param("transactionType") String transactionType,
                                             @Param("sourceType") String sourceType,
                                             @Param("sourceId") UUID sourceId,
                                             @Param("sourceLineId") UUID sourceLineId,
                                             @Param("productId") UUID productId,
                                             @Param("warehouseId") UUID warehouseId,
                                             @Param("locationId") UUID locationId,
                                             @Param("lotNo") String lotNo,
                                             @Param("occurredFrom") java.time.OffsetDateTime occurredFrom,
                                             @Param("occurredTo") java.time.OffsetDateTime occurredTo,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    /**
     * 统计流水查询总数。
     *
     * @param tenantId 可信租户
     * @param transactionType 可选交易类型
     * @param sourceType 可选来源类型
     * @param sourceId 可选来源 ID
     * @param sourceLineId 可选来源明细 ID
     * @param productId 可选产品
     * @param warehouseId 可选仓库
     * @param locationId 可选库位
     * @param lotNo 可选批次
     * @param occurredFrom 可选时间起点
     * @param occurredTo 可选时间终点
     * @return 总条数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
             FROM inv_inventory_transaction
             WHERE tenant_id = #{tenantId}
               AND isdel = 0
            <if test="transactionType != null and transactionType != ''"> AND transaction_type = #{transactionType}</if>
            <if test="sourceType != null and sourceType != ''"> AND source_type = #{sourceType}</if>
            <if test="sourceId != null"> AND source_id = #{sourceId}</if>
            <if test="sourceLineId != null"> AND source_line_id = #{sourceLineId}</if>
            <if test="productId != null"> AND (from_product_id = #{productId} OR to_product_id = #{productId})</if>
            <if test="warehouseId != null"> AND (from_warehouse_id = #{warehouseId} OR to_warehouse_id = #{warehouseId})</if>
            <if test="locationId != null"> AND (from_location_id = #{locationId} OR to_location_id = #{locationId})</if>
            <if test="lotNo != null"> AND (from_lot_no = #{lotNo} OR to_lot_no = #{lotNo})</if>
            <if test="occurredFrom != null"> AND occurred_at &gt;= #{occurredFrom}</if>
            <if test="occurredTo != null"> AND occurred_at &lt;= #{occurredTo}</if>
            </script>
            """)
    long count(@Param("tenantId") UUID tenantId,
               @Param("transactionType") String transactionType,
               @Param("sourceType") String sourceType,
               @Param("sourceId") UUID sourceId,
               @Param("sourceLineId") UUID sourceLineId,
               @Param("productId") UUID productId,
               @Param("warehouseId") UUID warehouseId,
               @Param("locationId") UUID locationId,
               @Param("lotNo") String lotNo,
               @Param("occurredFrom") java.time.OffsetDateTime occurredFrom,
               @Param("occurredTo") java.time.OffsetDateTime occurredTo);
}
