package com.ailearn.platform.core.purchasing.domain.port;

import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.PurchasingProductFact;
import java.util.Optional;
import java.util.UUID;

/**
 * 采购应用读取主数据的最小端口；实现必须显式按可信 tenant_id 过滤。
 */
public interface PurchasingReferencePort {

    /**
     * 查询当前租户内启用的供应商。
     */
    boolean isActiveSupplier(UUID tenantId, UUID supplierId);

    /**
     * 查询当前租户内启用的商品及其计量/批次事实。
     */
    Optional<PurchasingProductFact> findActiveProduct(UUID tenantId, UUID productId);

    /**
     * 查询当前租户内启用的仓库。
     */
    boolean isActiveWarehouse(UUID tenantId, UUID warehouseId);

    /**
     * 查询当前租户内启用的库位及所属仓库/类型。
     */
    Optional<PurchasingLocationFact> findActiveLocation(UUID tenantId, UUID locationId);
}
