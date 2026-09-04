package com.ailearn.platform.core.traceability.ports;

import java.util.Optional;
import java.util.UUID;

/** 制造领域 Facts 查询端口，包含 GIS 生产区域最小引用查询。 */
public interface ManufacturingFactsQuery {
    FactsSummary manufacturing(FactsQueryRequest request);
    FactsSummary traceSummary(FactsQueryRequest request);
    TraceFacts trace(TraceQuery query);
    Optional<ReferencedEntity> findProductionArea(FactsQueryContext context, UUID productionAreaId);
}
