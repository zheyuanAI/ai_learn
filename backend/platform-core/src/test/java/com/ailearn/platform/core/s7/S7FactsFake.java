package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.traceability.ports.FactQueryUnavailableException;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.PointStatusFacts;
import com.ailearn.platform.core.traceability.ports.PurchasingFactsQuery;
import com.ailearn.platform.core.traceability.ports.QualityFactsQuery;
import com.ailearn.platform.core.traceability.ports.ReferencedEntity;
import com.ailearn.platform.core.traceability.ports.SalesFactsQuery;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** S7 单元测试 fake，模拟未来 S5/S6 应用服务端口，不触碰任何 Mapper 或数据库。 */
public class S7FactsFake implements InventoryFactsQuery, PurchasingFactsQuery, SalesFactsQuery,
        ManufacturingFactsQuery, QualityFactsQuery, IotFactsPort {
    private final Map<String, FactsSummary> summaries = new HashMap<>();
    private final Map<UUID, ReferencedEntity> warehouses = new HashMap<>();
    private final Map<UUID, ReferencedEntity> productionAreas = new HashMap<>();
    private final Map<UUID, ReferencedEntity> devices = new HashMap<>();
    private final Map<UUID, PointStatusFacts> pointStatuses = new HashMap<>();
    private final Map<String, TraceFacts> traceFacts = new HashMap<>();
    private final Map<String, Boolean> unavailable = new HashMap<>();
    private int callCount;
    private int traceCallCount;

    public S7FactsFake() {
        Instant updated = Instant.parse("2026-09-04T00:00:00Z");
        for (String key : new String[]{"inventory", "fulfillment", "manufacturing", "quality", "device", "alarm", "traceability"}) {
            summaries.put(key, new FactsSummary(Map.of("" + key + "_count", BigDecimal.ONE), key, updated));
        }
    }

    public void putSummary(String key, FactsSummary summary) {
        summaries.put(key, summary);
    }

    public void putWarehouse(UUID id, ReferencedEntity entity) {
        warehouses.put(id, entity);
    }

    public void putProductionArea(UUID id, ReferencedEntity entity) {
        productionAreas.put(id, entity);
    }

    public void putDevice(UUID id, ReferencedEntity entity, PointStatusFacts status) {
        devices.put(id, entity);
        pointStatuses.put(id, status);
    }

    public void putTrace(String source, String entityType, UUID entityId, TraceFacts facts) {
        traceFacts.put(source + ":" + entityType + ":" + entityId, facts);
    }

    public void setUnavailable(String source, boolean value) {
        unavailable.put(source, value);
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public FactsSummary inventory(FactsQueryRequest request) {
        return summary("inventory");
    }

    @Override
    public FactsSummary fulfillment(FactsQueryRequest request) {
        return summary("fulfillment");
    }

    @Override
    public FactsSummary manufacturing(FactsQueryRequest request) {
        return summary("manufacturing");
    }

    @Override
    public FactsSummary quality(FactsQueryRequest request) {
        return summary("quality");
    }

    @Override
    public FactsSummary device(FactsQueryRequest request) {
        return summary("device");
    }

    @Override
    public FactsSummary alarm(FactsQueryRequest request) {
        return summary("alarm");
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        return summary("traceability");
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        String[] sourceOrder = {"inventory", "purchasing", "sales", "manufacturing", "quality", "iot"};
        String source = sourceOrder[traceCallCount++ % sourceOrder.length];
        if (unavailable.getOrDefault(source, false)) {
            throw new FactQueryUnavailableException(source + " unavailable");
        }
        return traceFacts.getOrDefault(source + ":" + query.entityType() + ":" + query.entityId(),
                TraceFacts.empty(source));
    }

    @Override
    public Optional<ReferencedEntity> findWarehouse(FactsQueryContext context, UUID warehouseId) {
        return visible(context, warehouses.get(warehouseId));
    }

    @Override
    public Optional<ReferencedEntity> findProductionArea(FactsQueryContext context, UUID productionAreaId) {
        return visible(context, productionAreas.get(productionAreaId));
    }

    @Override
    public Optional<ReferencedEntity> findDevice(FactsQueryContext context, UUID deviceId) {
        return visible(context, devices.get(deviceId));
    }

    @Override
    public Optional<PointStatusFacts> pointStatus(FactsQueryContext context, UUID deviceId) {
        return visible(context, devices.get(deviceId)).map(entity -> pointStatuses.get(deviceId));
    }

    private FactsSummary summary(String source) {
        callCount++;
        if (unavailable.getOrDefault(source, false)) {
            throw new FactQueryUnavailableException(source + " unavailable");
        }
        return summaries.get(source);
    }

    private static Optional<ReferencedEntity> visible(FactsQueryContext context, ReferencedEntity entity) {
        return Optional.ofNullable(entity).filter(value -> context.tenantId().equals(value.tenantId()));
    }

}
