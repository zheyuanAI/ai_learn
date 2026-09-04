package com.ailearn.platform.core.stocktake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryDecreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.stocktake.application.StocktakeApplicationServiceImpl;
import com.ailearn.platform.core.stocktake.domain.StocktakeLine;
import com.ailearn.platform.core.stocktake.domain.StocktakeOrder;
import com.ailearn.platform.core.stocktake.domain.StocktakeRepository;
import com.ailearn.platform.core.stocktake.domain.StocktakeStatus;
import com.ailearn.platform.core.stocktake.dto.StocktakeConfirmRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCountLineRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCreateRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeView;
import com.ailearn.platform.core.stocktake.exception.StocktakeException;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 盘点应用服务纯单元测试。
 * <p>
 * 不连接数据库，验证三段状态机、系统快照版本、预留不变量、差异原因、调整端口和幂等重放。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class StocktakeApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID LOCATION_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID LINE_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID TRANSACTION_ID = UUID.fromString("f0000000-0000-0000-0000-000000000003");
    private static final OffsetDateTime TIME = OffsetDateTime.of(2026, 9, 3, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String SESSION_ID = "jti-stocktake-test";
    private static final String REQUEST_ID = "request-stocktake-test";

    @Mock
    private StocktakeRepository repository;

    @Mock
    private InventoryQueryService inventoryQueryService;

    @Mock
    private InventoryCommandService inventoryCommandService;

    @Mock
    private WarehouseReferencePort warehouseReferencePort;

    @Mock
    private InventoryLocationPort inventoryLocationPort;

    private StocktakeApplicationServiceImpl service;

    /**
     * 设置可信操作上下文和服务。
     */
    @BeforeEach
    void setUpContext() {
        TenantContextHolder.setTenantId(TENANT_A);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti(SESSION_ID);
        RequestContextHolder.getContext().setRequestId(REQUEST_ID);
        service = new StocktakeApplicationServiceImpl(repository, inventoryQueryService, inventoryCommandService);
    }

    /**
     * 清理上下文。
     */
    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    /**
     * 创建盘点单初始状态为 NotStarted。
     */
    @Test
    void createStartsAsNotStarted() {
        when(repository.insert(any(StocktakeOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StocktakeView result = service.create(createRequest(), "stocktake-create-1");

        assertEquals("NotStarted", result.getStatus());
        assertEquals(1, result.getAllowedActions().size());
        assertEquals("start", result.getAllowedActions().getFirst().getAction());
    }

    @Test
    void strictProductionConstructorRejectsInactiveWarehouseBeforeInsert() {
        when(warehouseReferencePort.isActiveInTenant(TENANT_A, WAREHOUSE_ID)).thenReturn(false);
        StocktakeApplicationServiceImpl strictService = new StocktakeApplicationServiceImpl(repository,
                inventoryQueryService, inventoryCommandService,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper(), warehouseReferencePort, inventoryLocationPort);

        assertThrows(NotFoundException.class,
                () -> strictService.create(createRequest(), "stocktake-masterdata-1"));
        verify(repository, never()).insert(any(StocktakeOrder.class));
    }

    /**
     * 开始盘点保存系统数量和余额版本，并进入 Counting。
     */
    @Test
    void startFreezesSystemSnapshotAndVersion() {
        StocktakeOrder order = notStartedOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        when(inventoryQueryService.queryBalances(eq(new InventoryBalanceQuery(
                TENANT_A, null, WAREHOUSE_ID, null, null, 1, 200))))
                .thenReturn(new InventoryBalancePage(List.of(balance("10", "2", 7)), 1, 1, 200));
        when(repository.start(eq(TENANT_A), eq(ORDER_ID), eq(0L), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(repository.insertLines(eq(ORDER_ID), any(List.class), eq(USER_ID))).thenReturn(1);

        StocktakeView result = service.start(ORDER_ID, "stocktake-start-1");

        assertEquals("Counting", result.getStatus());
        assertEquals(1, result.getLines().size());
        assertEquals("10.000000", result.getLines().getFirst().systemQty());
        assertEquals(7, result.getLines().getFirst().systemBalanceVersion());
        verify(repository).insertLines(eq(ORDER_ID), any(List.class), eq(USER_ID));
    }

    /**
     * 差异确认必须填写原因，并且不在校验失败时调用库存调整。
     */
    @Test
    void varianceRequiresReason() {
        StocktakeOrder order = countingOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        stubCurrentBalance("10", "2", 7);

        assertThrows(StocktakeException.class,
                () -> service.confirm(ORDER_ID, countRequest("12", null), "stocktake-confirm-reason-1"));

        verify(inventoryCommandService, never()).increase(any(InventoryIncreaseCommand.class));
        verify(inventoryCommandService, never()).decrease(any(InventoryDecreaseCommand.class));
        verify(repository, never()).confirm(any(), any(), any(Long.class), any(List.class), any(), any());
    }

    /**
     * 正差异通过库存增加端口生成一条 ADJUSTMENT 流水并完成状态迁移。
     */
    @Test
    void positiveVarianceCreatesAdjustmentAndConfirms() {
        StocktakeOrder order = countingOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        stubCurrentBalance("10", "2", 7);
        when(inventoryCommandService.increase(any(InventoryIncreaseCommand.class))).thenReturn(mutationResult());
        when(repository.confirm(eq(TENANT_A), eq(ORDER_ID), eq(1L), any(List.class), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);

        StocktakeView result = service.confirm(ORDER_ID, countRequest("12", "盘点多出"), "stocktake-confirm-positive-1");

        assertEquals("ConfirmedAdjusted", result.getStatus());
        assertEquals(List.of(TRANSACTION_ID), result.getTransactionIds());
        assertEquals(TRANSACTION_ID, result.getLines().getFirst().adjustmentTransactionId());
        ArgumentCaptor<InventoryIncreaseCommand> captor = ArgumentCaptor.forClass(InventoryIncreaseCommand.class);
        verify(inventoryCommandService).increase(captor.capture());
        assertEquals("ADJUSTMENT", captor.getValue().metadata().transactionType());
        assertEquals("STOCKTAKE", captor.getValue().metadata().sourceType());
        assertEquals(7L, captor.getValue().expectedBalanceVersion());
        verify(repository).confirm(eq(TENANT_A), eq(ORDER_ID), eq(1L), any(List.class), eq(USER_ID), any(OffsetDateTime.class));
    }

    /**
     * 无差异也保存确认事实，但不得生成库存数量变化流水。
     */
    @Test
    void noVarianceConfirmsWithoutInventoryMutation() {
        StocktakeOrder order = countingOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        stubCurrentBalance("10", "2", 7);
        when(repository.confirm(eq(TENANT_A), eq(ORDER_ID), eq(1L), any(List.class), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);

        StocktakeView result = service.confirm(ORDER_ID, countRequest("10", null), "stocktake-confirm-no-diff-1");

        assertEquals("ConfirmedAdjusted", result.getStatus());
        assertEquals(List.of(), result.getTransactionIds());
        verify(inventoryCommandService, never()).increase(any(InventoryIncreaseCommand.class));
        verify(inventoryCommandService, never()).decrease(any(InventoryDecreaseCommand.class));
    }

    /**
     * 余额版本变化时拒绝确认，避免按过期系统快照调整。
     */
    @Test
    void changedBalanceVersionIsRejected() {
        StocktakeOrder order = countingOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        stubCurrentBalance("11", "2", 8);

        assertThrows(InventoryException.class,
                () -> service.confirm(ORDER_ID, countRequest("12", "版本变化"), "stocktake-confirm-version-1"));

        verify(inventoryCommandService, never()).increase(any(InventoryIncreaseCommand.class));
        verify(repository, never()).confirm(any(), any(), any(Long.class), any(List.class), any(), any());
    }

    /**
     * 实盘数量不能低于当前有效预留，否则会破坏余额不变量。
     */
    @Test
    void countedQtyCannotBeBelowReservedQty() {
        StocktakeOrder order = countingOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        stubCurrentBalance("10", "11", 7);

        assertThrows(StocktakeException.class,
                () -> service.confirm(ORDER_ID, countRequest("9", "预留占用"), "stocktake-confirm-reserved-1"));

        verify(inventoryCommandService, never()).decrease(any(InventoryDecreaseCommand.class));
    }

    /**
     * 已确认盘点不可再次确认。
     */
    @Test
    void confirmedOrderCannotBeConfirmedAgain() {
        StocktakeOrder order = new StocktakeOrder(ORDER_ID, TENANT_A, "ST-001", WAREHOUSE_ID, null,
                StocktakeStatus.ConfirmedAdjusted, 2L, USER_ID, TIME, USER_ID, TIME, USER_ID, TIME,
                USER_ID, TIME, List.of(line("10", 7, null, null)));
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(StocktakeException.class,
                () -> service.confirm(ORDER_ID, countRequest("10", null), "stocktake-confirmed-1"));

        verify(inventoryCommandService, never()).increase(any(InventoryIncreaseCommand.class));
    }

    /**
     * 同一确认载荷命中幂等重放，不重复生成调整。
     */
    @Test
    void sameConfirmPayloadIsIdempotent() {
        StocktakeOrder order = countingOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        stubCurrentBalance("10", "2", 7);
        when(inventoryCommandService.increase(any(InventoryIncreaseCommand.class))).thenReturn(mutationResult());
        when(repository.confirm(eq(TENANT_A), eq(ORDER_ID), eq(1L), any(List.class), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);

        StocktakeConfirmRequest request = countRequest("12", "重复请求");
        StocktakeView first = service.confirm(ORDER_ID, request, "stocktake-confirm-repeat-1");
        StocktakeView replay = service.confirm(ORDER_ID, request, "stocktake-confirm-repeat-1");

        assertEquals(first.getId(), replay.getId());
        assertEquals(first.getTransactionIds(), replay.getTransactionIds());
        verify(inventoryCommandService).increase(any(InventoryIncreaseCommand.class));
        verify(repository).confirm(eq(TENANT_A), eq(ORDER_ID), eq(1L), any(List.class), eq(USER_ID), any(OffsetDateTime.class));
    }

    /**
     * 构造创建请求。
     */
    private StocktakeCreateRequest createRequest() {
        StocktakeCreateRequest request = new StocktakeCreateRequest();
        request.setStocktakeNo("ST-001");
        request.setWarehouseId(WAREHOUSE_ID);
        return request;
    }

    /**
     * 构造未盘点聚合。
     */
    private StocktakeOrder notStartedOrder() {
        return new StocktakeOrder(ORDER_ID, TENANT_A, "ST-001", WAREHOUSE_ID, null,
                StocktakeStatus.NotStarted, 0L, null, null, null, null, USER_ID, TIME, USER_ID, TIME, List.of());
    }

    /**
     * 构造盘点中聚合。
     */
    private StocktakeOrder countingOrder() {
        return new StocktakeOrder(ORDER_ID, TENANT_A, "ST-001", WAREHOUSE_ID, null,
                StocktakeStatus.Counting, 1L, USER_ID, TIME, null, null, USER_ID, TIME, USER_ID, TIME,
                List.of(line("10", 7, null, null)));
    }

    /**
     * 构造快照明细。
     */
    private StocktakeLine line(String systemQty, long version, BigDecimal countedQty, String reason) {
        return new StocktakeLine(LINE_ID, TENANT_A, 1, PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, "LOT-1",
                qty(systemQty), version, countedQty, reason, null);
    }

    /**
     * 按测试目标 stub 精确余额查询。
     */
    private void stubCurrentBalance(String onHand, String reserved, long version) {
        when(inventoryQueryService.queryBalances(eq(new InventoryBalanceQuery(
                TENANT_A, PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, "LOT-1", 1, 2))))
                .thenReturn(new InventoryBalancePage(
                        List.of(balance(onHand, reserved, version)), 1, 1, 2));
    }

    /**
     * 构造确认请求。
     */
    private StocktakeConfirmRequest countRequest(String countedQty, String reason) {
        StocktakeCountLineRequest line = new StocktakeCountLineRequest();
        line.setLineId(LINE_ID);
        line.setCountedQty(countedQty);
        line.setVarianceReason(reason);
        StocktakeConfirmRequest request = new StocktakeConfirmRequest();
        request.setLines(List.of(line));
        return request;
    }

    /**
     * 构造当前库存余额。
     */
    private InventoryBalance balance(String onHand, String reserved, long version) {
        return new InventoryBalance(UUID.randomUUID(), TENANT_A,
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, "LOT-1"),
                qty(onHand), qty(reserved), version, TIME);
    }

    /**
     * 构造库存调整返回值。
     */
    private InventoryMutationResult mutationResult() {
        InventoryTransaction transaction = new InventoryTransaction(TRANSACTION_ID, TENANT_A, "INV-ST-1",
                "ADJUSTMENT", "STOCKTAKE", ORDER_ID, LINE_ID, null,
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, LOCATION_ID, "LOT-1"), qty("2"), TIME,
                USER_ID, SESSION_ID, REQUEST_ID, "inventory:increase|stocktake-line", "digest");
        return new InventoryMutationResult("INCREASE", qty("2"), List.of(), null, List.of(),
                List.of(transaction), java.util.Set.of("DECREASE"));
    }

    private BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
