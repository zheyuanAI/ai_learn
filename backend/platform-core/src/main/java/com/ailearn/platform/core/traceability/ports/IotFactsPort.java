package com.ailearn.platform.core.traceability.ports;

import java.util.Optional;
import java.util.UUID;

/** IoT 远程事实端口；Core S7 不直接访问 IoT 表或 Mapper。 */
public interface IotFactsPort {
    FactsSummary device(FactsQueryRequest request);
    FactsSummary alarm(FactsQueryRequest request);
    FactsSummary traceSummary(FactsQueryRequest request);
    TraceFacts trace(TraceQuery query);
    Optional<ReferencedEntity> findDevice(FactsQueryContext context, UUID deviceId);
    Optional<PointStatusFacts> pointStatus(FactsQueryContext context, UUID deviceId);
}
