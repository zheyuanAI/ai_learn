package com.ailearn.platform.core.sales.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

        SalesFulfillmentResult result = service.confirmPick(TASK_ID, request, "pick-test-1");

        assertEquals("4.000000", result.order().getLines().getFirst().reservedQty());
        assertEquals("4.000000", result.order().getLines().getFirst().pickedQty());
        verify(inventoryCommandService).reserve(any());
        verify(inventoryCommandService).move(any());
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

        SalesFulfillmentResult result = service.confirmShipment(SHIPMENT_ID, request, "ship-test-1");

        assertEquals("Completed", result.order().getStatus());
        assertEquals("5.000000", result.order().getLines().getFirst().shippedQty());
        verify(inventoryCommandService).release(any());
        verify(inventoryCommandService).decrease(any());
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
