package com.ailearn.platform.core.manufacturing.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.core.manufacturing.foundation.application.ManufacturingFoundationServiceImpl;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.SalesLineFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomComponentRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingOperationRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.exception.FoundationException;
import com.ailearn.platform.core.manufacturing.foundation.infrastructure.InMemoryFoundationRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * S5-foundation focused tests。
 * <p>
 * 测试只使用内存事实适配器，不连接数据库；重点验证有效版本、来源租户/产品约束、单一来源字段、拆分和幂等。
 * </p>
 */
class ManufacturingFoundationApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_A = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("c1000000-0000-0000-0000-000000000002");
    private static final UUID SALES_LINE_A = UUID.fromString("d1000000-0000-0000-0000-000000000001");

    private InMemoryFoundationRepository repository;
    private ManufacturingFoundationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_A);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti("foundation-test-jti");
        RequestContextHolder.getContext().setRequestId("foundation-test-request");
        repository = new InMemoryFoundationRepository();
        service = new ManufacturingFoundationServiceImpl(repository);
        repository.saveSalesLine(new SalesLineFact(SALES_LINE_A, TENANT_A, PRODUCT_A,
                new BigDecimal("100"), true));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    /** 有效 ACTIVE BOM/Routing 能被工单引用，工单保存锁定的版本号并从 Draft 开始。 */
    @Test
    void createsWorkOrderWithValidBomAndRoutingVersion() {
        BomFact bom = createBom(PRODUCT_A, "V1", BomStatus.ACTIVE, "bom-create-1");
        RoutingFact routing = createRouting(PRODUCT_A, "V2", RoutingStatus.ACTIVE, "routing-create-1");

        WorkOrderFact workOrder = service.createWorkOrder(workOrder(PRODUCT_A, bom.id(), routing.id(), null),
                "wo-create-1");

        assertEquals(WorkOrderStatus.Draft, workOrder.status());
        assertEquals("V1", workOrder.bomVersion());
        assertEquals("V2", workOrder.routingVersion());
        assertEquals(TENANT_A, workOrder.tenantId());
        assertTrue(repository.findActiveBom(TENANT_A, bom.id()).isPresent());
        assertTrue(repository.findActiveRouting(TENANT_A, routing.id()).isPresent());
    }

    /** 非 ACTIVE 或逻辑删除的 BOM/Routing 不得作为工单版本。 */
    @Test
    void rejectsInactiveBomOrRouting() {
        BomFact draftBom = createBom(PRODUCT_A, "D1", BomStatus.DRAFT, "bom-draft-1");
        RoutingFact activeRouting = createRouting(PRODUCT_A, "V1", RoutingStatus.ACTIVE, "routing-active-1");

        FoundationException exception = assertThrows(FoundationException.class,
                () -> service.createWorkOrder(workOrder(PRODUCT_A, draftBom.id(), activeRouting.id(), null),
                        "wo-invalid-bom-1"));

        assertEquals("MES_WO_005", exception.getBusinessCode());
    }

    /** 同一销售明细可以拆分到多个工单；无来源工单也可成功创建。 */
    @Test
    void allowsSplitSourceLineAndNoSource() {
        BomFact bom = createBom(PRODUCT_A, "V1", BomStatus.ACTIVE, "bom-split-1");
        RoutingFact routing = createRouting(PRODUCT_A, "V1", RoutingStatus.ACTIVE, "routing-split-1");

        WorkOrderFact first = service.createWorkOrder(
                workOrder(PRODUCT_A, bom.id(), routing.id(), SALES_LINE_A), "wo-split-1");
        WorkOrderFact second = service.createWorkOrder(
                workOrder(PRODUCT_A, bom.id(), routing.id(), SALES_LINE_A), "wo-split-2");
        WorkOrderFact noSource = service.createWorkOrder(
                workOrder(PRODUCT_A, bom.id(), routing.id(), null), "wo-no-source-1");

        assertEquals(SALES_LINE_A, first.sourceSalesOrderLineId());
        assertEquals(SALES_LINE_A, second.sourceSalesOrderLineId());
        assertNotEquals(first.id(), second.id());
        assertEquals(null, noSource.sourceSalesOrderLineId());
        assertEquals(3, repository.countWorkOrders(TENANT_A));
    }

    /** 销售来源跨租户或产品不一致时拒绝，不向工单仓储写入事实。 */
    @Test
    void rejectsCrossTenantAndProductMismatchSource() {
        BomFact bomA = createBom(PRODUCT_A, "V1", BomStatus.ACTIVE, "bom-source-a");
        RoutingFact routingA = createRouting(PRODUCT_A, "V1", RoutingStatus.ACTIVE, "routing-source-a");
        BomFact bomProductMismatch = createBom(PRODUCT_B, "V1", BomStatus.ACTIVE, "bom-source-product-mismatch");
        RoutingFact routingProductMismatch = createRouting(PRODUCT_B, "V1", RoutingStatus.ACTIVE,
                "routing-source-product-mismatch");
        FoundationException mismatch = assertThrows(FoundationException.class,
                () -> service.createWorkOrder(workOrder(PRODUCT_B, bomProductMismatch.id(), routingProductMismatch.id(),
                        SALES_LINE_A),
                        "wo-source-mismatch"));
        assertEquals("MES_WO_004", mismatch.getBusinessCode());

        TenantContextHolder.setTenantId(TENANT_B);
        BomFact bomB = createBom(PRODUCT_B, "V1", BomStatus.ACTIVE, "bom-source-b");
        RoutingFact routingB = createRouting(PRODUCT_B, "V1", RoutingStatus.ACTIVE, "routing-source-b");
        FoundationException crossTenant = assertThrows(FoundationException.class,
                () -> service.createWorkOrder(workOrder(PRODUCT_B, bomB.id(), routingB.id(), SALES_LINE_A),
                        "wo-source-cross-tenant"));
        assertEquals("MES_WO_004", crossTenant.getBusinessCode());
        assertEquals(0, repository.countWorkOrders(TENANT_B));
    }

    /** 同一租户、同一操作域和同一载荷的重复创建只返回第一次工单。 */
    @Test
    void replaysIdempotentWorkOrderCreation() {
        BomFact bom = createBom(PRODUCT_A, "V1", BomStatus.ACTIVE, "bom-idempotent-1");
        RoutingFact routing = createRouting(PRODUCT_A, "V1", RoutingStatus.ACTIVE, "routing-idempotent-1");
        WorkOrderCreateRequest request = workOrder(PRODUCT_A, bom.id(), routing.id(), null);

        WorkOrderFact first = service.createWorkOrder(request, "wo-idempotent-1");
        WorkOrderFact replay = service.createWorkOrder(request, "wo-idempotent-1");

        assertEquals(first, replay);
        assertEquals(1, repository.countWorkOrders(TENANT_A));
    }

    /** 同一幂等键提交不同载荷时返回冲突，不能生成第二个工单。 */
    @Test
    void rejectsDifferentPayloadWithSameIdempotencyKey() {
        BomFact bom = createBom(PRODUCT_A, "V1", BomStatus.ACTIVE, "bom-idempotent-2");
        RoutingFact routing = createRouting(PRODUCT_A, "V1", RoutingStatus.ACTIVE, "routing-idempotent-2");
        service.createWorkOrder(workOrder(PRODUCT_A, bom.id(), routing.id(), null), "wo-idempotent-2");

        FoundationException exception = assertThrows(FoundationException.class,
                () -> service.createWorkOrder(new WorkOrderCreateRequest(null, PRODUCT_A,
                        new BigDecimal("11"), START, FINISH, bom.id(), routing.id(), null),
                        "wo-idempotent-2"));

        assertEquals("MES_FOUNDATION_002", exception.getBusinessCode());
        assertEquals(1, repository.countWorkOrders(TENANT_A));
    }

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-09-03T08:00:00Z");
    private static final OffsetDateTime FINISH = OffsetDateTime.parse("2026-09-03T16:00:00Z");

    private BomFact createBom(UUID productId, String version, BomStatus status, String key) {
        return service.createBom(new BomCreateRequest("BOM-" + key, productId, version, status,
                List.of(new BomComponentRequest(PRODUCT_B, new BigDecimal("2"), "PCS", new BigDecimal("0.02")))), key);
    }

    private RoutingFact createRouting(UUID productId, String version, RoutingStatus status, String key) {
        return service.createRouting(new RoutingCreateRequest("ROUTING-" + key, productId, version, status,
                List.of(new RoutingOperationRequest(10, "装配", UUID.fromString("e1000000-0000-0000-0000-000000000001"),
                        new BigDecimal("30")))), key);
    }

    private WorkOrderCreateRequest workOrder(UUID productId, UUID bomId, UUID routingId, UUID sourceLineId) {
        return new WorkOrderCreateRequest(null, productId, new BigDecimal("10"), START, FINISH,
                bomId, routingId, sourceLineId);
    }
}
