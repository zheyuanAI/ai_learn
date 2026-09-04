package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.gis.application.GisApplicationService;
import com.ailearn.platform.core.gis.domain.DisplayStatus;
import com.ailearn.platform.core.gis.domain.MapAssetMetadata;
import com.ailearn.platform.core.gis.domain.MapEntityType;
import com.ailearn.platform.core.gis.domain.SiteMapConfiguration;
import com.ailearn.platform.core.gis.dto.CreateSiteMapCommand;
import com.ailearn.platform.core.gis.dto.MapPointProjection;
import com.ailearn.platform.core.gis.dto.SaveMapPointCommand;
import com.ailearn.platform.core.gis.dto.SiteMapProjection;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.gis.ports.InMemoryGisConfigurationStore;
import com.ailearn.platform.core.traceability.ports.PointStatusFacts;
import com.ailearn.platform.core.traceability.ports.ReferencedEntity;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GisApplicationServiceTest {
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void shouldSupportMultipleMapsPercentCoordinatesAndPointIdempotency() {
        S7FactsFake facts = new S7FactsFake();
        UUID deviceId = UUID.randomUUID();
        facts.putDevice(deviceId, entity(TENANT_A, "DEVICE", deviceId, true),
                new PointStatusFacts(false, false, false, null, null, null, Instant.now()));
        GisApplicationService service = service(facts);
        var context = S7TestSupport.context(TENANT_A, "perm-a", "gis:map:view", "gis:map:manage");

        SiteMapConfiguration first = service.createMap(context,
                new CreateSiteMapCommand("factory", "厂区", new MapAssetMetadata("factory.png", "image/png", 1024, HASH)));
        SiteMapConfiguration second = service.createMap(context,
                new CreateSiteMapCommand("workshop", "车间", new MapAssetMetadata("workshop.webp", "image/webp", 2048, HASH)));
        service.savePoint(context, new SaveMapPointCommand(first.id(), MapEntityType.DEVICE, deviceId,
                12.5, 88.0, 15, "/devices/" + deviceId), "point-key");
        var replay = service.savePoint(context, new SaveMapPointCommand(first.id(), MapEntityType.DEVICE, deviceId,
                12.5, 88.0, 15, "/devices/" + deviceId), "point-key");

        assertEquals(2, service.listMaps(context).size());
        assertEquals(first.id(), replay.siteMapId());
        assertEquals(1, service.getSiteMap(context, first.id(), null, null).points().size());
        assertEquals(0, service.getSiteMap(context, second.id(), null, null).points().size());
    }

    @Test
    void shouldRejectInvalidAssetCrossTenantPointAndPayloadConflict() {
        S7FactsFake facts = new S7FactsFake();
        UUID deviceId = UUID.randomUUID();
        facts.putDevice(deviceId, entity(TENANT_A, "DEVICE", deviceId, true),
                new PointStatusFacts(false, false, false, null, null, null, Instant.now()));
        GisApplicationService service = service(facts);
        var contextA = S7TestSupport.context(TENANT_A, "perm-a", "gis:map:view", "gis:map:manage");
        var contextB = S7TestSupport.context(TENANT_B, "perm-b", "gis:map:view", "gis:map:manage");
        SiteMapConfiguration map = service.createMap(contextA,
                new CreateSiteMapCommand("factory", "厂区", new MapAssetMetadata("factory.png", "image/png", 1024, HASH)));

        assertThrows(IllegalArgumentException.class,
                () -> new MapAssetMetadata("factory.gif", "image/gif", 1024, HASH));
        assertCode("GIS_TENANT_001", () -> service.savePoint(contextB,
                new SaveMapPointCommand(map.id(), MapEntityType.DEVICE, deviceId, 10, 10, 0, ""), "key-b"));
        service.savePoint(contextA, new SaveMapPointCommand(map.id(), MapEntityType.DEVICE, deviceId,
                10, 10, 0, ""), "same-key");
        assertCode("GIS_POINT_002", () -> service.savePoint(contextA,
                new SaveMapPointCommand(map.id(), MapEntityType.DEVICE, deviceId, 11, 10, 0, ""), "same-key"));
        assertCode("GIS_CONFIG_001", () -> service.savePoint(contextA,
                new SaveMapPointCommand(map.id(), MapEntityType.DEVICE, deviceId, 101, 10, 0, ""), "bad-coordinate"));
    }

    @Test
    void shouldApplyAlarmOfflineWarningNormalPriorityToDevicePoint() {
        S7FactsFake facts = new S7FactsFake();
        UUID deviceId = UUID.randomUUID();
        facts.putDevice(deviceId, entity(TENANT_A, "DEVICE", deviceId, true),
                new PointStatusFacts(true, true, true, "CRITICAL", "ACTIVE", Instant.now(), Instant.now()));
        GisApplicationService service = service(facts);
        var context = S7TestSupport.context(TENANT_A, "perm-a", "gis:map:view", "gis:map:manage");
        SiteMapConfiguration map = service.createMap(context,
                new CreateSiteMapCommand("factory", "厂区", new MapAssetMetadata("factory.png", "image/png", 1024, HASH)));
        service.savePoint(context, new SaveMapPointCommand(map.id(), MapEntityType.DEVICE, deviceId,
                50, 50, 0, ""), "priority");

        MapPointProjection projection = service.getPoint(context,
                service.getSiteMap(context, map.id(), null, null).points().get(0).pointId());
        assertEquals(DisplayStatus.ALARM, projection.displayStatus());
    }

    @Test
    void shouldRequireConfigurationAndViewPermissions() {
        S7FactsFake facts = new S7FactsFake();
        GisApplicationService service = service(facts);
        var context = S7TestSupport.context(TENANT_A, "perm-a");
        assertCode("GIS_AUTH_001", () -> service.listMaps(context));
        assertCode("GIS_AUTH_001", () -> service.createMap(context,
                new CreateSiteMapCommand("factory", "厂区", new MapAssetMetadata("factory.png", "image/png", 1024, HASH))));
    }

    @Test
    void shouldReplayPointAfterApplicationServiceRestartFromStoreIdempotencyRecord() {
        S7FactsFake facts = new S7FactsFake();
        UUID deviceId = UUID.randomUUID();
        facts.putDevice(deviceId, entity(TENANT_A, "DEVICE", deviceId, true),
                new PointStatusFacts(false, false, false, null, null, null, Instant.now()));
        InMemoryGisConfigurationStore store = new InMemoryGisConfigurationStore();
        var context = S7TestSupport.context(TENANT_A, "perm-a", "gis:map:view", "gis:map:manage");
        GisApplicationService first = new GisApplicationService(store, facts, facts, facts,
                java.time.Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneId.of("UTC")));
        SiteMapConfiguration map = first.createMap(context,
                new CreateSiteMapCommand("factory", "厂区",
                        new MapAssetMetadata("factory.png", "image/png", 1024, HASH)));
        SaveMapPointCommand command = new SaveMapPointCommand(map.id(), MapEntityType.DEVICE,
                deviceId, 10, 20, 0, "");
        MapPointProjection original = toProjection(first.savePoint(context, command, "restart-key"));

        GisApplicationService afterRestart = new GisApplicationService(store, facts, facts, facts,
                java.time.Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneId.of("UTC")));
        MapPointProjection replay = toProjection(afterRestart.savePoint(context, command, "restart-key"));

        assertEquals(original, replay);
        assertCode("GIS_POINT_002", () -> afterRestart.savePoint(context,
                new SaveMapPointCommand(map.id(), MapEntityType.DEVICE, deviceId, 11, 20, 0, ""),
                "restart-key"));
    }

    private static MapPointProjection toProjection(com.ailearn.platform.core.gis.domain.MapPointConfiguration point) {
        return new MapPointProjection(point.id(), point.entityType(), point.entityId(), "测试实体",
                point.xPercent(), point.yPercent(), point.rotation(), DisplayStatus.NORMAL,
                point.linkedPage(), Instant.parse("2026-09-04T00:00:00Z"));
    }

    private static GisApplicationService service(S7FactsFake facts) {
        return new GisApplicationService(new InMemoryGisConfigurationStore(), facts, facts, facts,
                java.time.Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneId.of("UTC")));
    }

    private static ReferencedEntity entity(UUID tenantId, String type, UUID id, boolean visible) {
        return new ReferencedEntity(tenantId, type, id, "测试实体", "Normal", "/detail/" + id,
                Instant.parse("2026-09-04T00:00:00Z"), visible);
    }

    private static void assertCode(String expected, Executable executable) {
        GisException exception = assertThrows(GisException.class, executable::run);
        assertEquals(expected, exception.getBusinessCode());
    }

    @FunctionalInterface
    private interface Executable {
        void run();
    }
}
