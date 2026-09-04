package com.ailearn.platform.core.traceability.ports;

import java.util.Optional;
import java.util.UUID;

/** 库存领域 Facts 查询端口；S7 不得注入库存 Mapper。 */
public interface InventoryFactsQuery {
    FactsSummary inventory(FactsQueryRequest request);
    FactsSummary traceSummary(FactsQueryRequest request);
    TraceFacts trace(TraceQuery query);
    Optional<ReferencedEntity> findWarehouse(FactsQueryContext context, UUID warehouseId);
}
