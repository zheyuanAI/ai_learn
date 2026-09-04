package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import java.util.UUID;

/**
 * 库存内核读取主数据库位状态的内部端口。
 * <p>
 * 该端口只读库位快照，不允许任何库存命令通过它修改主数据。
 * </p>
 */
public interface InventoryLocationPort {

    /**
     * 查询指定租户的库位快照。
     *
     * @param tenantId 可信租户 ID
     * @param locationId 库位 ID
     * @return 库位快照，不存在时返回 null
     */
    LocationSnapshot findByTenantIdAndId(UUID tenantId, UUID locationId);
}
