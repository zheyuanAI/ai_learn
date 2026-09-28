package com.ailearn.platform.core.sales.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.*;
import com.ailearn.platform.core.inventory.domain.*;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.sales.domain.*;
import com.ailearn.platform.core.sales.dto.*;
import com.ailearn.platform.core.sales.fulfillment.application.SalesFulfillmentApplicationServiceImpl;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** 实际销售应用服务的确定性锁交错回归；库存替身保留事务锁并检测等待环，不依赖数据库或随机休眠。 */
class SalesFulfillmentLockConcurrencyTest {
    private static final UUID TENANT = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID SHIPPING = UUID.fromString("e1000000-0000-0000-0000-000000000001");
    private static final UUID STORAGE = UUID.fromString("e1000000-0000-0000-0000-000000000002");
    private static final UUID PICK_ORDER = UUID.fromString("f1000000-0000-0000-0000-000000000001");
    private static final UUID RETURN_ORDER = UUID.fromString("f1000000-0000-0000-0000-000000000002");
    private static final UUID PICK_LINE = UUID.fromString("f1000000-0000-0000-0000-000000000003");
    private static final UUID RETURN_LINE = UUID.fromString("f1000000-0000-0000-0000-000000000004");
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-09-27T00:00:00Z");
    private static final InventoryDimension SOURCE = new InventoryDimension(PRODUCT, WAREHOUSE, STORAGE, "LOT-1");
    private static final InventoryDimension STAGED = SOURCE.withLocation(SHIPPING);

    /**
     * 用途：复现订单 A 自动预留源位与订单 B 退拣的反向余额锁，并验证完整预锁使两个事务都成功。
     * 入参：无；出参：无；流程：旧调用时固定 A 持源、B 持暂存的交错；预锁后同序串行，等待图无环。
     */
    @Test
    void directPickAndReturnPickDoNotCreateWaitCycle() throws Exception {
        SalesOrderRepository orders = mock(SalesOrderRepository.class);
        InventoryCommandService commands = mock(InventoryCommandService.class);
        InventoryQueryService queries = mock(InventoryQueryService.class);
        InventoryLocationPort locations = mock(InventoryLocationPort.class);
        HeldBalanceLocks locks = new HeldBalanceLocks();
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch returnHeldShipping = new CountDownLatch(1);
        when(orders.findById(eq(TENANT), any())).thenAnswer(invocation -> Optional.of(
                invocation.getArgument(1).equals(PICK_ORDER) ? order(PICK_ORDER, PICK_LINE, false)
                        : order(RETURN_ORDER, RETURN_LINE, true)));
        when(orders.updateFulfillment(any(), any(Long.class), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(locations.findByTenantIdAndId(TENANT, STORAGE)).thenReturn(
                new LocationSnapshot(STORAGE, TENANT, WAREHOUSE, LocationType.Storage, "ACTIVE"));
        when(locations.findByTenantIdAndId(TENANT, SHIPPING)).thenReturn(
                new LocationSnapshot(SHIPPING, TENANT, WAREHOUSE, LocationType.ShippingStaging, "ACTIVE"));
        InventoryReservation returnedReservation = reservation(RETURN_ORDER, RETURN_LINE);
        InventoryReservationAllocation returnedAllocation = allocation(returnedReservation, STAGED);
        when(queries.queryReservations(any())).thenAnswer(invocation -> {
            InventoryReservationQuery query = invocation.getArgument(0);
            return new InventoryReservationPage(query.sourceId().equals(PICK_ORDER) ? List.of()
                    : List.of(new InventoryReservationView(returnedReservation, List.of(returnedAllocation))),
                    query.sourceId().equals(PICK_ORDER) ? 0 : 1, 1, 200);
        });
        when(queries.queryBalances(any())).thenReturn(new InventoryBalancePage(List.of(
                new InventoryBalance(UUID.randomUUID(), TENANT, SOURCE, qty("10"), qty("0"), 0, TIME)), 1, 1, 200));
        doAnswer(invocation -> {
            Collection<InventoryDimension> dimensions = invocation.getArgument(0);
            for (InventoryDimension dimension : dimensions.stream().distinct()
                    .sorted(Comparator.comparing(value -> value.lockKey(TENANT))).toList()) {
                locks.acquire(dimension);
            }
            return null;
        }).when(commands).lockBalances(any());
        when(commands.reserve(any())).thenAnswer(invocation -> {
            InventoryReserveCommand command = invocation.getArgument(0);
            locks.acquire(command.dimension());
            reserved.countDown();
            // 只有旧流程未提前持有暂存锁时才固定反向交错，修复后的同序锁不会等待这个门闩。
            if (!locks.heldByCurrentThread(STAGED)) {
                assertTrue(returnHeldShipping.await(5, TimeUnit.SECONDS), "退拣未取得暂存位锁");
            }
            InventoryReservation reservation = reservation(PICK_ORDER, PICK_LINE);
            return mutation(reservation, List.of(allocation(reservation, command.dimension())));
        });
        when(commands.move(any())).thenAnswer(invocation -> {
            InventoryMoveCommand command = invocation.getArgument(0);
            for (InventoryDimension dimension : List.of(command.fromDimension(), command.toDimension()).stream()
                    .sorted(Comparator.comparing(value -> value.lockKey(TENANT))).toList()) {
                locks.acquire(dimension);
                if (command.fromDimension().equals(STAGED) && dimension.equals(STAGED)) {
                    returnHeldShipping.countDown();
                }
            }
            return mutation(null, List.of());
        });
        SalesFulfillmentApplicationServiceImpl service = new SalesFulfillmentApplicationServiceImpl(
                orders, commands, queries, locations);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<String> pick = workers.submit(() -> withTransactionLocks(locks, () -> {
                PickLineRequest line = new PickLineRequest();
                line.setSalesOrderLineId(PICK_LINE);
                line.setPickedQty("2");
                line.setSourceLocationId(STORAGE);
                line.setShippingLocationId(SHIPPING);
                PickTaskConfirmRequest request = new PickTaskConfirmRequest();
                request.setSalesOrderId(PICK_ORDER);
                request.setLines(List.of(line));
                return service.confirmPick(request, "concurrent-pick").action();
            }));
            Future<String> returned = workers.submit(() -> withTransactionLocks(locks, () -> {
                assertTrue(reserved.await(5, TimeUnit.SECONDS), "拣货未取得源位锁");
                PickTaskReturnLineRequest line = new PickTaskReturnLineRequest();
                line.setSalesOrderLineId(RETURN_LINE);
                line.setReturnQty("2");
                line.setToLocationId(STORAGE);
                PickTaskReturnRequest request = new PickTaskReturnRequest();
                request.setSalesOrderId(RETURN_ORDER);
                request.setLines(List.of(line));
                return service.returnPick(request, "concurrent-return").action();
            }));
            assertEquals("PICK", pick.get(10, TimeUnit.SECONDS));
            assertEquals("PICK_RETURN", returned.get(10, TimeUnit.SECONDS));
            assertEquals(0, locks.detectedCycles);
        } finally {
            workers.shutdownNow();
        }
    }

    /** 入参：锁替身和业务动作；出参：动作结果；流程：模拟事务持锁周期并清理线程身份。 */
    private <T> T withTransactionLocks(HeldBalanceLocks locks, Callable<T> action) throws Exception {
        TenantContextHolder.setTenantId(TENANT);
        RequestContextHolder.getContext().setUserId(USER);
        RequestContextHolder.getContext().setJti("concurrency-jti");
        RequestContextHolder.getContext().setRequestId(Thread.currentThread().getName());
        try {
            return action.call();
        } finally {
            locks.releaseCurrentThread();
            UserContextHolder.clear();
        }
    }

    /** 入参：订单、行ID及是否已拣货；出参：已审核订单；流程：两订单共享库存但保存独立履约数量。 */
    private SalesOrder order(UUID id, UUID lineId, boolean picked) {
        SalesOrderLine line = new SalesOrderLine(lineId, TENANT, 1, PRODUCT, "EA", qty("5"),
                qty(picked ? "2" : "0"), qty(picked ? "2" : "0"), qty("0"));
        return new SalesOrder(id, TENANT, "SO-" + id, UUID.randomUUID(), null, SalesOrderStatus.Approved,
                null, null, null, null, null, null, 0, USER, TIME, USER, TIME, List.of(line));
    }

    /** 入参：来源订单与行；出参：两件有效预留；流程：构造与请求一致的来源事实。 */
    private InventoryReservation reservation(UUID orderId, UUID lineId) {
        return new InventoryReservation(UUID.randomUUID(), TENANT, "RES-" + orderId, "SALES_ORDER",
                orderId, lineId, qty("2"), qty("0"), "Active", 0, TIME, TIME);
    }

    /** 入参：预留和维度；出参：两件分配；流程：保持批次与产品不变。 */
    private InventoryReservationAllocation allocation(InventoryReservation reservation, InventoryDimension dimension) {
        return new InventoryReservationAllocation(UUID.randomUUID(), TENANT, reservation.id(), dimension,
                qty("2"), qty("0"), 0, TIME, TIME);
    }

    /** 入参：预留与分配；出参：库存替身响应；流程：提供业务应用要求的流水事实及关联标识。 */
    private InventoryMutationResult mutation(InventoryReservation reservation, List<InventoryReservationAllocation> allocations) {
        InventoryTransaction transaction = new InventoryTransaction(UUID.randomUUID(), TENANT, "TX-" + UUID.randomUUID(),
                "MOVE", "SALES_ORDER", PICK_ORDER, PICK_LINE, SOURCE, STAGED, qty("2"), TIME,
                USER, "concurrency-jti", "concurrency-request", "concurrency-key", "digest");
        return new InventoryMutationResult("MOVE", qty("2"), List.of(), reservation, allocations,
                List.of(transaction), Set.of());
    }

    /** 入参：数量文本；出参：六位精度数量；流程：复用库存精度基线。 */
    private BigDecimal qty(String value) { return new BigDecimal(value).setScale(6); }

    /** 模拟数据库事务余额锁；等待图出现环时直接报告，超时只作为测试挂起保护。 */
    private static final class HeldBalanceLocks {
        private final Map<InventoryDimension, Thread> owners = new HashMap<>();
        private final Map<Thread, Thread> waiting = new HashMap<>();
        private int detectedCycles;

        /** 入参：余额维度；出参：无；流程：重入或等待持有者，检测当前线程是否处于循环等待。 */
        synchronized void acquire(InventoryDimension dimension) throws InterruptedException {
            Thread current = Thread.currentThread();
            while (owners.containsKey(dimension) && owners.get(dimension) != current) {
                Thread owner = owners.get(dimension);
                waiting.put(current, owner);
                Thread cursor = owner;
                Set<Thread> visited = new HashSet<>();
                while (cursor != null && visited.add(cursor)) {
                    if (cursor == current) {
                        detectedCycles++;
                        throw new IllegalStateException("确定性余额锁循环等待");
                    }
                    cursor = waiting.get(cursor);
                }
                wait(5000);
            }
            waiting.remove(current);
            owners.put(dimension, current);
        }

        /** 入参：余额维度；出参：当前线程是否持锁；流程：判断是否已完成全集合预锁。 */
        synchronized boolean heldByCurrentThread(InventoryDimension dimension) {
            return owners.get(dimension) == Thread.currentThread();
        }

        /** 入参：无；出参：无；流程：模拟事务结束时释放全部锁并唤醒其他事务。 */
        synchronized void releaseCurrentThread() {
            Thread current = Thread.currentThread();
            owners.values().removeIf(owner -> owner == current);
            waiting.remove(current);
            notifyAll();
        }
    }
}
