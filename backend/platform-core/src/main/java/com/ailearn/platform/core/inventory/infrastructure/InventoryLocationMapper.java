package com.ailearn.platform.core.inventory.infrastructure;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 库位主数据只读 Mapper。
 */
@Mapper
public interface InventoryLocationMapper {

    /**
     * 查询租户内未删除库位的类型和状态。
     *
     * @param tenantId 可信租户
     * @param locationId 库位 ID
     * @return 库位行，不存在时返回 null
     */
    @Select("""
            SELECT id, tenant_id, warehouse_id, type, status, isdel
              FROM md_location
             WHERE tenant_id = #{tenantId}
               AND id = #{locationId}
               AND isdel = 0
             LIMIT 1
            """)
    @Results(id = "locationRowMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "tenantId", column = "tenant_id"),
            @Result(property = "warehouseId", column = "warehouse_id"),
            @Result(property = "locationType", column = "type"),
            @Result(property = "status", column = "status"),
            @Result(property = "isdel", column = "isdel")
    })
    InventoryLocationRow selectByTenantAndId(@Param("tenantId") UUID tenantId,
                                             @Param("locationId") UUID locationId);
}
