package com.ailearn.platform.core.masterdata.infrastructure;

import com.ailearn.platform.core.masterdata.domain.entity.Warehouse;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.masterdata.infrastructure.repository.WarehouseRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 基于仓库主数据 Repository 的引用校验适配器。
 */
@Component
@ConditionalOnMissingBean(WarehouseReferencePort.class)
public class WarehouseReferenceAdapter implements WarehouseReferencePort {

    private final WarehouseRepository warehouseRepository;

    public WarehouseReferenceAdapter(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * 在当前租户范围内查询仓库，并拒绝停用仓库作为新库位归属。
     *
     * @param tenantId 当前可信租户
     * @param warehouseId 仓库 ID
     * @return 仓库存在、未删除且启用时返回 true
     */
    @Override
    public boolean isActiveInTenant(UUID tenantId, UUID warehouseId) {
        if (warehouseId == null) {
            return false;
        }
        return warehouseRepository.findById(tenantId, warehouseId)
                .map(Warehouse::getStatus)
                .filter("ACTIVE"::equalsIgnoreCase)
                .isPresent();
    }
}
