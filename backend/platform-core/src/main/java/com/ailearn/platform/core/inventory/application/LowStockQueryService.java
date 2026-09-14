package com.ailearn.platform.core.inventory.application;

import java.util.UUID;

/** 库存与商品安全库存阈值的受控只读聚合端口。 */
public interface LowStockQueryService {
    /** 按可信租户和可选仓库查询低库存产品，最多返回 limit 条。 */
    LowStockPage query(UUID tenantId, UUID warehouseId, int limit);
}
