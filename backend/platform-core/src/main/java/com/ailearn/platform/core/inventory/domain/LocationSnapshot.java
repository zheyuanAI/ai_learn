package com.ailearn.platform.core.inventory.domain;

import java.util.UUID;

/**
 * 库位应用快照，供库存内核做租户、状态和类型校验。
 *
 * @param id 库位 ID
 * @param tenantId 租户 ID
 * @param warehouseId 所属仓库；旧适配器未提供时可为空
 * @param type 库位类型
 * @param status 库位状态
 */
public record LocationSnapshot(UUID id, UUID tenantId, UUID warehouseId,
                               LocationType type, String status) {

    /**
     * 兼容只携带库位类型和状态的测试/旧适配器构造方式。
     *
     * @param id 库位 ID
     * @param tenantId 所属租户
     * @param type 库位类型
     * @param status 库位状态
     */
    public LocationSnapshot(UUID id, UUID tenantId, LocationType type, String status) {
        this(id, tenantId, null, type, status);
    }

    /**
     * 判断库位是否处于可操作状态。
     *
     * @return 状态为 ACTIVE 时返回 true
     */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
