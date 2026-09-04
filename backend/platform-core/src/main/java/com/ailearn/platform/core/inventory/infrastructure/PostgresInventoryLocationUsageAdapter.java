package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import com.ailearn.platform.core.masterdata.domain.port.LocationUsagePort;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 将库存余额事实适配为主数据库位停用前置检查端口。
 * <p>
 * 主数据应用服务不直接依赖库存 Mapper；该适配器位于 inventory 基础设施层，按租户聚合余额后提供只读快照。
 * </p>
 */
@Component
@Primary
public class PostgresInventoryLocationUsageAdapter implements LocationUsagePort {

    private final InventoryRepository inventoryRepository;

    /**
     * 创建库位库存使用量适配器。
     *
     * @param inventoryRepository 库存事实持久化边界
     */
    public PostgresInventoryLocationUsageAdapter(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * 读取指定租户库位的实物与有效预留总量。
     * 入参：可信租户和库位 ID；出参：当前库存使用快照；流程：委托库存 Repository 聚合余额并返回。
     *
     * @param tenantId 可信租户
     * @param locationId 库位 ID
     * @return 库位使用快照
     */
    @Override
    public LocationUsageSnapshot getUsage(UUID tenantId, UUID locationId) {
        return inventoryRepository.queryLocationUsage(tenantId, locationId);
    }
}
