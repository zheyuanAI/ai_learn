package com.ailearn.platform.core.masterdata.domain.port;

import java.util.UUID;

/**
 * 库位创建/修改使用的仓库引用查询端口。
 * <p>
 * 只返回当前租户内仍可见的仓库，避免库位绑定到跨租户或已逻辑删除的仓库。
 * </p>
 */
public interface WarehouseReferencePort {

    /**
     * 判断仓库是否属于当前租户且处于启用状态。
     *
     * @param tenantId 当前可信租户
     * @param warehouseId 请求中的仓库 ID
     * @return 可作为库位归属的仓库返回 true
     */
    boolean isActiveInTenant(UUID tenantId, UUID warehouseId);
}
