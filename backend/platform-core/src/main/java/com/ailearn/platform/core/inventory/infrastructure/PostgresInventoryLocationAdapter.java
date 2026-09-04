package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 基于 PostgreSQL 主数据表读取库位状态的适配器。
 * <p>
 * 适配器只读 {@code md_location}，库存内核不会通过该适配器修改库位主数据。
 * </p>
 */
@Repository
public class PostgresInventoryLocationAdapter implements InventoryLocationPort {

    private final InventoryLocationMapper mapper;

    /**
     * 创建库位查询适配器。
     *
     * @param mapper 库位只读 Mapper
     */
    public PostgresInventoryLocationAdapter(InventoryLocationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按租户读取未删除库位，并转换为库存领域快照。
     *
     * @param tenantId 可信租户 ID
     * @param locationId 库位 ID
     * @return 库位快照，不存在时返回 null
     */
    @Override
    public LocationSnapshot findByTenantIdAndId(UUID tenantId, UUID locationId) {
        try {
            InventoryLocationRow row = mapper.selectByTenantAndId(tenantId, locationId);
            if (row == null) {
                return null;
            }
            return new LocationSnapshot(row.getId(), row.getTenantId(), row.getWarehouseId(),
                    LocationType.parse(row.getLocationType()), row.getStatus());
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("库存库位主数据暂时不可用", exception);
        }
    }
}
