package com.ailearn.platform.core.sales.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.InventoryReservationPage;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.sales.domain.SalesFulfillmentFact;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.sales.dto.PickLineRequest;
import com.ailearn.platform.core.sales.dto.PickTaskConfirmRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnLineRequest;
import com.ailearn.platform.core.sales.dto.SalesFulfillmentResult;
import com.ailearn.platform.core.sales.dto.ShipmentConfirmRequest;
import com.ailearn.platform.core.sales.dto.ShipmentLineRequest;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import com.ailearn.platform.core.sales.fulfillment.application.SalesFulfillmentApplicationServiceImpl;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 销售履约应用服务单元测试，验证库存命令唯一入口、自动预留、发货释放和订单数量累计。
 */
@ExtendWith(MockitoExtension.class)
class SalesFulfillmentApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_LOCATION_ID = UUID.fromString("e1000000-0000-0000-0000-000000000001");
    private static final UUID SHIPPING_LOCATION_ID = UUID.fromString("e1000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID LINE_ID = UUID.fromString("f1000000-0000-0000-0000-000000000002");
    private static final UUID TASK_ID = UUID.fromString("f1000000-0000-0000-0000-000000000003");
    private static final UUID SHIPMENT_ID = UUID.fromString("f1000000-0000-0000-0000-000000000004");
    private static final UUID RESERVATION_ID = UUID.fromString("f1000000-0000-0000-0000-000000000005");
    private static final UUID ALLOCATION_ID = UUID.fromString("f1000000-0000-0000-0000-000000000006");
    private static final OffsetDateTime TIME = OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private SalesOrderRepository repository;
    @Mock
    private InventoryCommandService inventoryCommandService;
    @Mock
    private InventoryQueryService inventoryQueryService;
    @Mock
    private InventoryLocationPort locationPort;

    private SalesFulfillmentApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti("jti-sales-fulfillment-test");
        RequestContextHolder.getContext().setRequestId("request-sales-fulfillment-test");
        service = new SalesFulfillmentApplicationServiceImpl(repository, inventoryCommandService,
                inventoryQueryService, locationPort);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void directPickAutoReservesAndMovesCoupledAllocation() {
        SalesOrder order = order(line("10", "0", "0", "0"), 0);
        when(repository.findById(TENANT_ID, ORDER_ID)).thenReturn(java.util.Optional.of(order));
        when(repository.updateFulfillment(any(SalesOrder.class), eq(0L), any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        activeLocations();
        when(inventoryQueryService.queryReservations(any())).thenReturn(
                new InventoryReservationPage(List.of(), 0, 1, 200));
        InventoryDimension sourceDimension = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID,
                SOURCE_LOCATION_ID, "");
        when(inventoryQueryService.queryBalances(any())).thenReturn(new InventoryBalancePage(
                List.of(new com.ailearn.platform.core.inventory.domain.InventoryBalance(
                        UUID.randomUUID(), TENANT_ID, sourceDimension, qty("10"), qty("0"), 0L, TIME)),
                1, 1, 200));
        when(inventoryCommandService.reserve(any())).thenReturn(mutation(
                reservation(RESERVATION_ID, "4", "0"),
                List.of(allocation(ALLOCATION_ID, RESERVATION_ID, sourceDimension, "4", "0")),
                "RESERVE"));
        when(inventoryCommandService.move(any())).thenReturn(mutation(null, List.of(), "MOVE"));

        PickLineRequest lineRequest = new PickLineRequest();
        lineRequest.setSalesOrderLineId(LINE_ID);
        lineRequest.setPickedQty("4");
        lineRequest.setSourceLocationId(SOURCE_LOCATION_ID);
        lineRequest.setShippingLocationId(SHIPPING_LOCATION_ID);
        PickTaskConfirmRequest request = new PickTaskConfirmRequest();
        request.setSalesOrderId(ORDER_ID);
        request.setLines(List.of(lineRequest));

        SalesFulfillmentResult result = service.confirmPick(request, "pick-test-1");

        assertEquals("4.000000", result.order().getLines().getFirst().reservedQty());
        assertEquals("4.000000", result.order().getLines().getFirst().pickedQty());
        assertNotEquals(ORDER_ID, result.operationId());
        assertNotEquals(LINE_ID, result.operationId());
        verify(inventoryCommandService).reserve(any());
        verify(inventoryCommandService).move(any());
        // 修改用途：拣货必须先锁完整源/目标集合，禁止先预留较大维度再请求较小暂存维度。
        var locks = ArgumentCaptor.forClass(java.util.Collection.class);
        var commandOrder = inOrder(inventoryCommandService);
        commandOrder.verify(inventoryCommandService).lockBalances(locks.capture());
        commandOrder.verify(inventoryCommandService).reserve(any());
        commandOrder.verify(inventoryCommandService).move(any());
        assertEquals(java.util.Set.of(sourceDimension,
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SHIPPING_LOCATION_ID, "")),
                new java.util.HashSet<>(locks.getValue()));
        ArgumentCaptor<List<SalesFulfillmentFact>> facts = ArgumentCaptor.forClass(List.class);
        verify(repository).updateFulfillment(any(SalesOrder.class), eq(0L), facts.capture());
        assertEquals(ORDER_ID, facts.getValue().getFirst().salesOrderId());
    }

    @Test
    void shipmentReleasesReservationThenDecreasesStagingInventory() {
        SalesOrder order = order(line("5", "5", "5", "0"), 1);
        when(repository.findById(TENANT_ID, ORDER_ID)).thenReturn(java.util.Optional.of(order));
        when(repository.updateFulfillment(any(SalesOrder.class), eq(1L), any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LocationSnapshot shipping = new LocationSnapshot(SHIPPING_LOCATION_ID, TENANT_ID, WAREHOUSE_ID,
                LocationType.ShippingStaging, "ACTIVE");
        when(locationPort.findByTenantIdAndId(TENANT_ID, SHIPPING_LOCATION_ID)).thenReturn(shipping);
        InventoryDimension dimension = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SHIPPING_LOCATION_ID, "");
        InventoryReservation reservation = reservation(RESERVATION_ID, "5", "0");
        InventoryReservationAllocation allocation = allocation(ALLOCATION_ID, RESERVATION_ID, dimension, "5", "0");
        when(inventoryQueryService.queryReservations(any())).thenReturn(new InventoryReservationPage(
                List.of(new com.ailearn.platform.core.inventory.application.InventoryReservationView(
                        reservation, List.of(allocation))), 1, 1, 200));
        when(inventoryCommandService.release(any())).thenReturn(mutation(reservation, List.of(allocation), "RELEASE"));
        when(inventoryCommandService.decrease(any())).thenReturn(mutation(null, List.of(), "DECREASE"));

        ShipmentLineRequest lineRequest = new ShipmentLineRequest();
        lineRequest.setSalesOrderLineId(LINE_ID);
        lineRequest.setProductId(PRODUCT_ID);
        lineRequest.setShipQty("5");
        ShipmentConfirmRequest request = new ShipmentConfirmRequest();
        request.setSalesOrderId(ORDER_ID);
        request.setShipTime(TIME);
        request.setShipmentLines(List.of(lineRequest));

        SalesFulfillmentResult result = service.confirmShipment(request, "ship-test-1");

        assertEquals("Completed", result.order().getStatus());
        assertEquals("5.000000", result.order().getLines().getFirst().shippedQty());
        assertNotEquals(ORDER_ID, result.operationId());
        assertNotEquals(LINE_ID, result.operationId());
        verify(inventoryCommandService).release(any());
        verify(inventoryCommandService).decrease(any());
        // 修改用途：多步发货在释放任何预留前预锁整批余额，避免跨行逆序持锁。
        var commandOrder = inOrder(inventoryCommandService);
        commandOrder.verify(inventoryCommandService).lockBalances(any());
        commandOrder.verify(inventoryCommandService).release(any());
        commandOrder.verify(inventoryCommandService).decrease(any());
    }

    /**
     * 用途：验证退拣按与直接拣货相同的完整余额集合预锁，来源方向不影响锁集合。
     * 入参：无；出参：无；流程：构造暂存预留，退回普通位，验证预锁发生在 move 前且保留批次。
     */
    @Test
    void returnPickLocksBothBalancesBeforeMovingReservation() {
        SalesOrder order = order(line("5", "5", "5", "0"), 1);
        when(repository.findById(TENANT_ID, ORDER_ID)).thenReturn(java.util.Optional.of(order));
        when(repository.updateFulfillment(any(SalesOrder.class), eq(1L), any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        activeLocations();
        InventoryDimension staged = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SHIPPING_LOCATION_ID, "LOT-1");
        InventoryReservation reservation = reservation(RESERVATION_ID, "5", "0");
        InventoryReservationAllocation allocation = allocation(ALLOCATION_ID, RESERVATION_ID, staged, "5", "0");
        when(inventoryQueryService.queryReservations(any())).thenReturn(new InventoryReservationPage(
                List.of(new com.ailearn.platform.core.inventory.application.InventoryReservationView(
                        reservation, List.of(allocation))), 1, 1, 200));
        when(inventoryCommandService.move(any())).thenReturn(mutation(null, List.of(), "MOVE"));
        PickTaskReturnLineRequest lineRequest = new PickTaskReturnLineRequest();
        lineRequest.setSalesOrderLineId(LINE_ID);
        lineRequest.setReturnQty("5");
        lineRequest.setToLocationId(SOURCE_LOCATION_ID);
        PickTaskReturnRequest request = new PickTaskReturnRequest();
        request.setSalesOrderId(ORDER_ID);
        request.setLines(List.of(lineRequest));

        service.returnPick(TASK_ID, request, "return-lock-order");

        var locks = ArgumentCaptor.forClass(java.util.Collection.class);
        var commandOrder = inOrder(inventoryCommandService);
        commandOrder.verify(inventoryCommandService).lockBalances(locks.capture());
        commandOrder.verify(inventoryCommandService).move(any());
        assertEquals(java.util.Set.of(staged, staged.withLocation(SOURCE_LOCATION_ID)),
                new java.util.HashSet<>(locks.getValue()));
    }

    /**
     * 用途：验证请求明细 B→A 时也在首次预留前预锁 A、B 的源/目标全集合。
     * 入参：无；出参：无；流程：模拟两物料反序拣货，捕获一次全集合预锁及之后的两组 reserve/move。
     */
    @Test
    void reverseLinePickLocksAllProductsBeforeFirstReservation() {
        UUID secondLineId = UUID.fromString("f1000000-0000-0000-0000-000000000007");
        UUID secondProductId = UUID.fromString("c1000000-0000-0000-0000-000000000002");
        SalesOrderLine firstLine = line("5", "0", "0", "0");
        SalesOrderLine secondLine = new SalesOrderLine(secondLineId, TENANT_ID, 2, secondProductId, "件",
                qty("5"), qty("0"), qty("0"), qty("0"));
        SalesOrder order = new SalesOrder(ORDER_ID, TENANT_ID, "SO-1", UUID.randomUUID(), null,
                SalesOrderStatus.Approved, null, null, null, null, null, null, 0L,
                USER_ID, TIME, USER_ID, TIME, List.of(firstLine, secondLine));
        when(repository.findById(TENANT_ID, ORDER_ID)).thenReturn(java.util.Optional.of(order));
        when(repository.updateFulfillment(any(SalesOrder.class), eq(0L), any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        activeLocations();
        when(inventoryQueryService.queryReservations(any())).thenReturn(new InventoryReservationPage(List.of(), 0, 1, 200));
        when(inventoryQueryService.queryBalances(any())).thenAnswer(invocation -> {
            var query = (com.ailearn.platform.core.inventory.application.InventoryBalanceQuery) invocation.getArgument(0);
            InventoryDimension dimension = new InventoryDimension(query.productId(), WAREHOUSE_ID, SOURCE_LOCATION_ID, "");
            return new InventoryBalancePage(List.of(new com.ailearn.platform.core.inventory.domain.InventoryBalance(
                    UUID.randomUUID(), TENANT_ID, dimension, qty("10"), qty("0"), 0L, TIME)), 1, 1, 200);
        });
        when(inventoryCommandService.reserve(any())).thenAnswer(invocation -> {
            var command = (com.ailearn.platform.core.inventory.application.InventoryReserveCommand) invocation.getArgument(0);
            UUID reservationId = UUID.randomUUID();
            InventoryReservation reservation = new InventoryReservation(reservationId, TENANT_ID,
                    "RES-" + reservationId, "SALES_ORDER", ORDER_ID, command.metadata().sourceLineId(),
                    command.quantity(), qty("0"), "Active", 0L, TIME, TIME);
            return mutation(reservation, List.of(allocation(UUID.randomUUID(), reservationId,
                    command.dimension(), "2", "0")), "RESERVE");
        });
        when(inventoryCommandService.move(any())).thenReturn(mutation(null, List.of(), "MOVE"));
        PickTaskConfirmRequest request = new PickTaskConfirmRequest();
        request.setSalesOrderId(ORDER_ID);
        request.setLines(List.of(pickLine(secondLineId), pickLine(LINE_ID)));

        service.confirmPick(TASK_ID, request, "pick-reverse-products");

        var locks = ArgumentCaptor.forClass(java.util.Collection.class);
        var commandOrder = inOrder(inventoryCommandService);
        commandOrder.verify(inventoryCommandService).lockBalances(locks.capture());
        commandOrder.verify(inventoryCommandService).reserve(any());
        commandOrder.verify(inventoryCommandService).move(any());
        commandOrder.verify(inventoryCommandService).reserve(any());
        commandOrder.verify(inventoryCommandService).move(any());
        assertEquals(java.util.Set.of(
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SOURCE_LOCATION_ID, ""),
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SHIPPING_LOCATION_ID, ""),
                new InventoryDimension(secondProductId, WAREHOUSE_ID, SOURCE_LOCATION_ID, ""),
                new InventoryDimension(secondProductId, WAREHOUSE_ID, SHIPPING_LOCATION_ID, "")),
                new java.util.HashSet<>(locks.getValue()));
    }

    /** 入参：订单行 ID；出参：拣货两件的请求；流程：复用测试的固定源位和暂存位。 */
    /** 同产品多行预锁前选批次必须扣除前行计划量，保持原逐行预留后的批次选择结果。 */
    @Test
    void sameProductLinesPlanDifferentLotsWhenFirstLotIsConsumed() {
        UUID secondLineId = UUID.randomUUID();
        SalesOrderLine firstLine = line("5", "0", "0", "0");
        SalesOrderLine secondLine = new SalesOrderLine(secondLineId, TENANT_ID, 2, PRODUCT_ID, "件",
                qty("5"), qty("0"), qty("0"), qty("0"));
        SalesOrder order = new SalesOrder(ORDER_ID, TENANT_ID, "SO-1", UUID.randomUUID(), null,
                SalesOrderStatus.Approved, null, null, null, null, null, null, 0L,
                USER_ID, TIME, USER_ID, TIME, List.of(firstLine, secondLine));
        when(repository.findById(TENANT_ID, ORDER_ID)).thenReturn(java.util.Optional.of(order));
        when(repository.updateFulfillment(any(SalesOrder.class), eq(0L), any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        activeLocations();
        when(inventoryQueryService.queryReservations(any())).thenReturn(new InventoryReservationPage(List.of(), 0, 1, 200));
        InventoryDimension lotA = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SOURCE_LOCATION_ID, "LOT-A");
        InventoryDimension lotB = new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, SOURCE_LOCATION_ID, "LOT-B");
        when(inventoryQueryService.queryBalances(any())).thenReturn(new InventoryBalancePage(List.of(
                new com.ailearn.platform.core.inventory.domain.InventoryBalance(UUID.randomUUID(), TENANT_ID,
                        lotA, qty("5"), qty("0"), 0L, TIME),
                new com.ailearn.platform.core.inventory.domain.InventoryBalance(UUID.randomUUID(), TENANT_ID,
                        lotB, qty("4"), qty("0"), 0L, TIME)), 2, 1, 200));
        when(inventoryCommandService.reserve(any())).thenAnswer(invocation -> {
            var command = (com.ailearn.platform.core.inventory.application.InventoryReserveCommand) invocation.getArgument(0);
            UUID reservationId = UUID.randomUUID();
            InventoryReservation reservation = new InventoryReservation(reservationId, TENANT_ID,
                    "RES-" + reservationId, "SALES_ORDER", ORDER_ID, command.metadata().sourceLineId(),
                    command.quantity(), qty("0"), "Active", 0L, TIME, TIME);
            return mutation(reservation, List.of(allocation(UUID.randomUUID(), reservationId,
                    command.dimension(), "4", "0")), "RESERVE");
        });
        when(inventoryCommandService.move(any())).thenReturn(mutation(null, List.of(), "MOVE"));
        PickLineRequest firstRequest = pickLine(LINE_ID);
        firstRequest.setPickedQty("4");
        PickLineRequest secondRequest = pickLine(secondLineId);
        secondRequest.setPickedQty("4");
        PickTaskConfirmRequest request = new PickTaskConfirmRequest();
        request.setSalesOrderId(ORDER_ID);
        request.setLines(List.of(firstRequest, secondRequest));

        service.confirmPick(TASK_ID, request, "two-lines-two-lots");

        var commands = ArgumentCaptor.forClass(com.ailearn.platform.core.inventory.application.InventoryReserveCommand.class);
        verify(inventoryCommandService, org.mockito.Mockito.times(2)).reserve(commands.capture());
        assertEquals(List.of(lotA, lotB), commands.getAllValues().stream().map(command -> command.dimension()).toList());
        var locks = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(inventoryCommandService).lockBalances(locks.capture());
        assertEquals(java.util.Set.of(lotA, lotB, lotA.withLocation(SHIPPING_LOCATION_ID),
                lotB.withLocation(SHIPPING_LOCATION_ID)), new java.util.HashSet<>(locks.getValue()));
    }

    private PickLineRequest pickLine(UUID lineId) {
        PickLineRequest request = new PickLineRequest();
        request.setSalesOrderLineId(lineId);
        request.setPickedQty("2");
        request.setSourceLocationId(SOURCE_LOCATION_ID);
        request.setShippingLocationId(SHIPPING_LOCATION_ID);
        return request;
    }

    @Test
    void pickRejectsNonApprovedOrderBeforeInventoryMutation() {
        SalesOrder order = new SalesOrder(ORDER_ID, TENANT_ID, "SO-1",
                UUID.randomUUID(), null, SalesOrderStatus.Draft, null, null, null, null, null,
                null, 0L, USER_ID, TIME, USER_ID, TIME, List.of(line("1", "0", "0", "0")));
        when(repository.findById(TENANT_ID, ORDER_ID)).thenReturn(java.util.Optional.of(order));
        PickLineRequest lineRequest = new PickLineRequest();
        lineRequest.setSalesOrderLineId(LINE_ID);
        lineRequest.setPickedQty("1");
        lineRequest.setSourceLocationId(SOURCE_LOCATION_ID);
        lineRequest.setShippingLocationId(SHIPPING_LOCATION_ID);
        PickTaskConfirmRequest request = new PickTaskConfirmRequest();
        request.setSalesOrderId(ORDER_ID);
        request.setLines(List.of(lineRequest));

        assertThrows(SalesOrderException.class, () -> service.confirmPick(TASK_ID, request, "pick-invalid-1"));
        verify(inventoryCommandService, never()).reserve(any());
        verify(inventoryCommandService, never()).move(any());
    }

    private void activeLocations() {
        when(locationPort.findByTenantIdAndId(TENANT_ID, SOURCE_LOCATION_ID))
                .thenReturn(new LocationSnapshot(SOURCE_LOCATION_ID, TENANT_ID, WAREHOUSE_ID,
                        LocationType.Storage, "ACTIVE"));
        when(locationPort.findByTenantIdAndId(TENANT_ID, SHIPPING_LOCATION_ID))
                .thenReturn(new LocationSnapshot(SHIPPING_LOCATION_ID, TENANT_ID, WAREHOUSE_ID,
                        LocationType.ShippingStaging, "ACTIVE"));
    }

    private SalesOrder order(SalesOrderLine line, long version) {
        return new SalesOrder(ORDER_ID, TENANT_ID, "SO-1", UUID.randomUUID(), null,
                SalesOrderStatus.Approved, null, null, null, null, null, null, version,
                USER_ID, TIME, USER_ID, TIME, List.of(line));
    }

    private SalesOrderLine line(String ordered, String reserved, String picked, String shipped) {
        return new SalesOrderLine(LINE_ID, TENANT_ID, 1, PRODUCT_ID, "件", qty(ordered), qty(reserved),
                qty(picked), qty(shipped));
    }

    private InventoryReservation reservation(UUID id, String reserved, String released) {
        return new InventoryReservation(id, TENANT_ID, "RES-1", "SALES_ORDER", ORDER_ID, LINE_ID,
                qty(reserved), qty(released), "Active", 0L, TIME, TIME);
    }

    private InventoryReservationAllocation allocation(UUID id, UUID reservationId, InventoryDimension dimension,
                                                      String allocated, String released) {
        return new InventoryReservationAllocation(id, TENANT_ID, reservationId, dimension,
                qty(allocated), qty(released), 0L, TIME, TIME);
    }

    private InventoryMutationResult mutation(InventoryReservation reservation,
                                             List<InventoryReservationAllocation> allocations, String operation) {
        InventoryTransaction transaction = new InventoryTransaction(UUID.randomUUID(), TENANT_ID, "INV-1",
                operation, "SALES_ORDER", ORDER_ID, LINE_ID, null, null, qty("1"), TIME, USER_ID,
                "jti-sales-fulfillment-test", "request-sales-fulfillment-test", operation, "digest");
        return new InventoryMutationResult(operation, qty("1"), List.of(), reservation, allocations,
                List.of(transaction), java.util.Set.of());
    }

    private BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
