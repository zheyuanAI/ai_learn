package com.ailearn.platform.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryAllocationMoveCommand;
import com.ailearn.platform.core.inventory.application.InventoryApplicationService;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryDecreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.application.InventoryReleaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryReserveCommand;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.inventory.infrastructure.InventoryRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ForbiddenException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 库存唯一写应用服务纯单元测试。
 * <p>
 * 不连接数据库，通过库存 Repository 和库位读取端口验证租户、数量、库位类型、锁定顺序、预留同步、
 * 幂等和查询委托边界。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final String SESSION_ID = "jti-inventory-test";
    private static final String REQUEST_ID = "request-inventory-test";
    private static final OffsetDateTime BUSINESS_TIME = OffsetDateTime.of(
            2026, 9, 3, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private InventoryRepository repository;

    @Mock
    private InventoryLocationPort locationPort;

    private InventoryApplicationService service;

    /**
     * 设置可信租户、用户、会话和请求上下文。
     */
    @BeforeEach
    void setUpContext() {
        TenantContextHolder.setTenantId(TENANT_A);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti(SESSION_ID);
        RequestContextHolder.getContext().setRequestId(REQUEST_ID);
        service = new InventoryApplicationService(repository, locationPort);
    }

    /**
     * 清理 ThreadLocal，避免库存测试之间串租户或串会话。
     */
    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    /**
     * 增加库存使用可信租户和用户，并追加目标库位流水。
     */
    @Test
    void increaseUsesTrustedContextAndAppendsTransaction() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000001");
        InventoryBalance locked = balance(dimension, "10", "2", 3);
        InventoryBalance updated = balance(dimension, "12", "2", 4);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);
        when(repository.updateBalance(eq(locked), eq(qty("12")), eq(qty("2")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updated);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMutationResult result = service.increase(
                new InventoryIncreaseCommand(metadata("increase-1", "RECEIPT"), dimension, qty("2")));

        assertEquals(qty("12"), result.balances().getFirst().onHandQty());
        assertEquals(qty("10"), result.balances().getFirst().availableQty());
        assertEquals(dimension, result.transactions().getFirst().toDimension());
        verify(repository).lockOrCreateBalance(TENANT_A, dimension, USER_ID);
    }

    /**
     * 减少库存只能使用可用量，预留占用后不足时不得更新余额或追加流水。
     */
    @Test
    void decreaseRejectsWhenAvailableQtyIsInsufficient() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000002");
        InventoryBalance locked = balance(dimension, "10", "8", 1);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);

        assertThrows(InventoryException.class, () -> service.decrease(
                new InventoryDecreaseCommand(metadata("decrease-1", "SHIPMENT"), dimension, qty("3"))));

        verify(repository, never()).updateBalance(any(), any(), any(), any(), any());
        verify(repository, never()).appendTransaction(any());
    }

    /**
     * 基于库存快照的调整必须在余额行锁定后再次校验版本，拒绝查询后发生过并发变更的命令。
     */
    @Test
    void increaseRejectsWhenExpectedBalanceVersionChanged() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000010");
        InventoryBalance locked = balance(dimension, "10", "2", 8);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);

        assertThrows(InventoryException.class, () -> service.increase(new InventoryIncreaseCommand(
                metadata("increase-version-1", "ADJUSTMENT"), dimension, qty("1"), 7L)));

        verify(repository, never()).updateBalance(any(), any(), any(), any(), any());
        verify(repository, never()).appendTransaction(any());
    }

    /**
     * 位置移动锁定来源和目标两侧余额，并同时保持总实物不变。
     */
    @Test
    void moveLocksBothBalancesAndPreservesPhysicalTotal() {
        InventoryDimension from = dimension("10000000-0000-0000-0000-000000000003");
        InventoryDimension to = dimension("10000000-0000-0000-0000-000000000004");
        InventoryBalance source = balance(from, "10", "2", 1);
        InventoryBalance target = balance(to, "1", "0", 7);
        InventoryBalance updatedSource = balance(from, "7", "2", 2);
        InventoryBalance updatedTarget = balance(to, "4", "0", 8);
        activeLocation(from, LocationType.Storage);
        activeLocation(to, LocationType.Picking);
        when(repository.lockBalancesInStableOrder(eq(TENANT_A), eq(List.of(from, to)), eq(USER_ID)))
                .thenReturn(List.of(target, source));
        when(repository.updateBalance(eq(source), eq(qty("7")), eq(qty("2")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updatedSource);
        when(repository.updateBalance(eq(target), eq(qty("4")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updatedTarget);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMutationResult result = service.move(
                new InventoryMoveCommand(metadata("move-1", "PUTAWAY"), from, to, qty("3")));

        assertEquals(qty("11"), result.balances().stream()
                .map(InventoryBalance::onHandQty).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(from, result.transactions().getFirst().fromDimension());
        assertEquals(to, result.transactions().getFirst().toDimension());
        verify(repository).lockBalancesInStableOrder(TENANT_A, List.of(from, to), USER_ID);
    }

    /**
     * 质量隔离位可以接收实物但不能创建正常库存预留。
     */
    @Test
    void reserveRejectsQualityHold() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000005");
        activeLocation(dimension, LocationType.QualityHold);

        assertThrows(InventoryException.class, () -> service.reserve(
                new InventoryReserveCommand(metadata("reserve-quality-hold", "RESERVE"), dimension, qty("1"))));

        verify(repository, never()).lockOrCreateBalance(any(), any(), any());
    }

    /**
     * 创建预留会在同一应用服务事务边界内增加余额预留、写入分配并追加流水。
     */
    @Test
    void reserveUpdatesBalanceAndCreatesAllocation() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000006");
        InventoryBalance locked = balance(dimension, "10", "1", 2);
        InventoryBalance updated = balance(dimension, "10", "4", 3);
        activeLocation(dimension, LocationType.Picking);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);
        when(repository.updateBalance(eq(locked), eq(qty("10")), eq(qty("4")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updated);
        when(repository.insertAllocation(any(InventoryReservationAllocation.class), eq(USER_ID)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMutationResult result = service.reserve(new InventoryReserveCommand(
                metadata("reserve-1", "RESERVE"), dimension, qty("3"), "RES-001"));

        assertEquals(qty("3"), result.reservation().reservedQty());
        assertEquals(1, result.allocations().size());
        assertEquals(dimension, result.allocations().getFirst().dimension());
        assertEquals(qty("6"), result.balances().getFirst().availableQty());
    }

    /**
     * 释放预留同步减少余额预留、分配有效量和预留状态，并生成释放流水。
     */
    @Test
    void releaseUpdatesAllocationAndReservation() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000007");
        UUID reservationId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID allocationId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        InventoryBalance locked = balance(dimension, "10", "5", 3);
        InventoryBalance updated = balance(dimension, "10", "2", 4);
        InventoryReservation reservation = reservation(reservationId, "5", "0", 1);
        InventoryReservation updatedReservation = reservation(reservationId, "5", "3", 2);
        InventoryReservationAllocation allocation = allocation(allocationId, reservationId, dimension, "5", "0", 1);
        InventoryReservationAllocation released = allocation(allocationId, reservationId, dimension, "5", "3", 2);
        activeLocation(dimension, LocationType.Picking);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);
        when(repository.lockReservation(TENANT_A, reservationId)).thenReturn(reservation);
        when(repository.lockActiveAllocations(TENANT_A, reservationId, allocationId, dimension))
                .thenReturn(List.of(allocation));
        when(repository.releaseAllocation(eq(allocation), eq(qty("3")), eq(USER_ID))).thenReturn(released);
        when(repository.updateBalance(eq(locked), eq(qty("10")), eq(qty("2")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updated);
        when(repository.releaseReservation(eq(reservation), eq(qty("3")), eq(USER_ID)))
                .thenReturn(updatedReservation);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMutationResult result = service.release(
                new InventoryReleaseCommand(metadata("release-1", "RELEASE"), reservationId,
                        dimension, qty("3"), allocationId));

        assertEquals(qty("3"), result.reservation().releasedQty());
        assertEquals(qty("3"), result.allocations().getFirst().releasedQty());
        assertEquals(qty("8"), result.balances().getFirst().availableQty());
    }

    /**
     * 实物移动携带预留分配时，来源和目标的实物与有效预留同步迁移。
     */
    @Test
    void moveWithReservationMovesPhysicalAndAllocationTogether() {
        InventoryDimension from = dimension("10000000-0000-0000-0000-000000000008");
        InventoryDimension to = dimension("10000000-0000-0000-0000-000000000009");
        UUID reservationId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        UUID allocationId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        InventoryBalance source = balance(from, "10", "5", 1);
        InventoryBalance target = balance(to, "1", "0", 2);
        InventoryBalance updatedSource = balance(from, "7", "2", 2);
        InventoryBalance updatedTarget = balance(to, "4", "3", 3);
        InventoryReservation reservation = reservation(reservationId, "5", "0", 1);
        InventoryReservationAllocation allocation = allocation(allocationId, reservationId, from, "5", "0", 1);
        InventoryReservationAllocation moved = allocation(allocationId, reservationId, to, "5", "0", 2);
        activeLocation(from, LocationType.Picking);
        activeLocation(to, LocationType.ShippingStaging);
        when(repository.lockBalancesInStableOrder(eq(TENANT_A), eq(List.of(from, to)), eq(USER_ID)))
                .thenReturn(List.of(source, target));
        when(repository.lockReservation(TENANT_A, reservationId)).thenReturn(reservation);
        when(repository.lockActiveAllocations(TENANT_A, reservationId, allocationId, from))
                .thenReturn(List.of(allocation));
        when(repository.moveAllocation(eq(allocation), eq(to), eq(qty("3")), eq(USER_ID)))
                .thenReturn(List.of(moved));
        when(repository.updateBalance(eq(source), eq(qty("7")), eq(qty("2")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updatedSource);
        when(repository.updateBalance(eq(target), eq(qty("4")), eq(qty("3")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updatedTarget);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryMutationResult result = service.move(new InventoryMoveCommand(
                metadata("move-reservation-1", "PICK"), from, to, qty("3"), reservationId, allocationId));

        assertEquals(qty("5"), result.balances().getFirst().availableQty());
        assertEquals(qty("1"), result.balances().getLast().availableQty());
        assertEquals(to, result.allocations().getFirst().dimension());
    }

    /**
     * 同一租户和同一载荷的幂等重放返回首次结果，不重复写余额或流水。
     */
    @Test
    void samePayloadIdempotencyReplaysFirstResult() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000010");
        InventoryBalance locked = balance(dimension, "1", "0", 1);
        InventoryBalance updated = balance(dimension, "2", "0", 2);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);
        when(repository.updateBalance(eq(locked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updated);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InventoryIncreaseCommand command = new InventoryIncreaseCommand(
                metadata("same-key", "RECEIPT"), dimension, qty("1"));

        InventoryMutationResult first = service.increase(command);
        InventoryMutationResult replay = service.increase(command);

        assertEquals(first, replay);
        verify(repository).updateBalance(eq(locked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID));
    }

    /**
     * 同一幂等键提交不同载荷时返回冲突，不允许产生第二笔库存事实。
     */
    @Test
    void differentPayloadWithSameIdempotencyKeyIsRejected() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000011");
        InventoryBalance locked = balance(dimension, "1", "0", 1);
        InventoryBalance updated = balance(dimension, "2", "0", 2);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);
        when(repository.updateBalance(eq(locked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updated);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.increase(new InventoryIncreaseCommand(
                metadata("conflict-key", "RECEIPT"), dimension, qty("1")));

        assertThrows(InventoryException.class, () -> service.increase(new InventoryIncreaseCommand(
                metadata("conflict-key", "RECEIPT", "digest-conflict-different"), dimension, qty("2"))));
        verify(repository).lockOrCreateBalance(TENANT_A, dimension, USER_ID);
    }

    /**
     * 即使调用方伪造相同 payloadDigest，只要业务数量发生变化，服务端摘要也必须拒绝重放。
     */
    @Test
    void serverDigestRejectsChangedPayloadWhenClientDigestStaysSame() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000013");
        InventoryBalance locked = balance(dimension, "1", "0", 1);
        InventoryBalance updated = balance(dimension, "2", "0", 2);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID)).thenReturn(locked);
        when(repository.updateBalance(eq(locked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(updated);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.increase(new InventoryIncreaseCommand(
                metadata("server-digest-key", "RECEIPT", "client-fixed-digest"), dimension, qty("1")));

        assertThrows(InventoryException.class, () -> service.increase(new InventoryIncreaseCommand(
                metadata("server-digest-key", "RECEIPT", "client-fixed-digest"), dimension, qty("2"))));
        verify(repository).updateBalance(eq(locked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID));
    }

    /**
     * 不同库存命令使用同一原始 Key 时按操作域隔离，不应错误重放另一命令。
     */
    @Test
    void sameRawKeyIsIsolatedByInventoryOperation() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000014");
        InventoryBalance increaseLocked = balance(dimension, "1", "0", 1);
        InventoryBalance increaseUpdated = balance(dimension, "2", "0", 2);
        InventoryBalance decreaseLocked = balance(dimension, "2", "0", 2);
        InventoryBalance decreaseUpdated = balance(dimension, "1", "0", 3);
        activeLocation(dimension, LocationType.Storage);
        when(repository.lockOrCreateBalance(TENANT_A, dimension, USER_ID))
                .thenReturn(increaseLocked, decreaseLocked);
        when(repository.updateBalance(eq(increaseLocked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(increaseUpdated);
        when(repository.updateBalance(eq(decreaseLocked), eq(qty("1")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID))).thenReturn(decreaseUpdated);
        when(repository.appendTransaction(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.increase(new InventoryIncreaseCommand(
                metadata("shared-raw-key", "RECEIPT"), dimension, qty("1")));
        service.decrease(new InventoryDecreaseCommand(
                metadata("shared-raw-key", "SHIPMENT"), dimension, qty("1")));

        verify(repository).updateBalance(eq(increaseLocked), eq(qty("2")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID));
        verify(repository).updateBalance(eq(decreaseLocked), eq(qty("1")), eq(qty("0")),
                eq(BUSINESS_TIME), eq(USER_ID));
    }

    /**
     * 普通移动不得把库存产品或批次改成目标维度的身份。
     */
    @Test
    void moveRejectsProductOrLotIdentityChange() {
        InventoryDimension from = dimension("10000000-0000-0000-0000-000000000015");
        InventoryDimension to = new InventoryDimension(
                UUID.fromString("10000000-0000-0000-0000-000000000099"), from.warehouseId(),
                UUID.fromString("10000000-0000-0000-0000-000000000016"), "LOT-2");
        activeLocation(from, LocationType.Storage);
        activeLocation(to, LocationType.Storage);

        assertThrows(InventoryException.class, () -> service.move(new InventoryMoveCommand(
                metadata("identity-change-key", "PUTAWAY"), from, to, qty("1"))));

        verify(repository, never()).lockBalancesInStableOrder(any(), any(), any());
    }

    /**
     * 维度携带的仓库必须与主数据库位所属仓库一致。
     */
    @Test
    void warehouseAndLocationOwnershipMustMatch() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000017");
        UUID otherWarehouse = UUID.fromString("50000000-0000-0000-0000-000000000099");
        when(locationPort.findByTenantIdAndId(TENANT_A, dimension.locationId()))
                .thenReturn(new LocationSnapshot(dimension.locationId(), TENANT_A, otherWarehouse,
                        LocationType.Storage, "ACTIVE"));

        assertThrows(InventoryException.class, () -> service.increase(new InventoryIncreaseCommand(
                metadata("warehouse-location-key", "RECEIPT"), dimension, qty("1"))));

        verify(repository, never()).lockOrCreateBalance(any(), any(), any());
    }

    /**
     * 客户端伪造其他租户元数据时，在访问库存 Repository 前被可信上下文拒绝。
     */
    @Test
    void forgedTenantMetadataIsRejectedBeforePersistence() {
        InventoryDimension dimension = dimension("10000000-0000-0000-0000-000000000012");

        assertThrows(ForbiddenException.class, () -> service.increase(new InventoryIncreaseCommand(
                new InventoryCommandMetadata(TENANT_B, USER_ID, SESSION_ID, REQUEST_ID, "tenant-key",
                        "digest", "RECEIPT", SOURCE_ID, null, "RECEIPT", BUSINESS_TIME),
                dimension, qty("1"))));

        verify(repository, never()).lockOrCreateBalance(any(), any(), any());
    }

    /**
     * 查询只把可信租户传给持久化边界，并保留库存页返回值。
     */
    @Test
    void queryUsesTrustedTenant() {
        InventoryBalancePage page = new InventoryBalancePage(List.of(), 0, 1, 50);
        when(repository.queryBalances(eq(TENANT_A), any(InventoryBalanceQuery.class))).thenReturn(page);

        InventoryBalancePage result = service.queryBalances(new InventoryBalanceQuery(TENANT_A));

        assertSame(page, result);
        verify(repository).queryBalances(eq(TENANT_A), any(InventoryBalanceQuery.class));
    }

    /**
     * 构造当前租户的命令元数据。
     *
     * @param key 幂等键
     * @param transactionType 交易类型
     * @return 测试命令元数据
     */
    private InventoryCommandMetadata metadata(String key, String transactionType) {
        return new InventoryCommandMetadata(TENANT_A, USER_ID, SESSION_ID, REQUEST_ID, key,
                "digest-" + key, "TEST", SOURCE_ID, null, transactionType, BUSINESS_TIME);
    }

    /**
     * 构造指定载荷摘要的命令元数据，用于验证同幂等键的载荷冲突。
     *
     * @param key 幂等键
     * @param transactionType 交易类型
     * @param payloadDigest 载荷摘要
     * @return 测试命令元数据
     */
    private InventoryCommandMetadata metadata(String key, String transactionType, String payloadDigest) {
        return new InventoryCommandMetadata(TENANT_A, USER_ID, SESSION_ID, REQUEST_ID, key,
                payloadDigest, "TEST", SOURCE_ID, null, transactionType, BUSINESS_TIME);
    }

    /**
     * 构造测试库存维度。
     *
     * @param locationId 库位 ID 文本
     * @return 测试库存维度
     */
    private InventoryDimension dimension(String locationId) {
        return new InventoryDimension(UUID.fromString("40000000-0000-0000-0000-000000000001"),
                UUID.fromString("50000000-0000-0000-0000-000000000001"), UUID.fromString(locationId), "LOT-1");
    }

    /**
     * 为指定维度 stub 启用库位。
     *
     * @param dimension 库存维度
     * @param type 库位类型
     */
    private void activeLocation(InventoryDimension dimension, LocationType type) {
        when(locationPort.findByTenantIdAndId(TENANT_A, dimension.locationId()))
                .thenReturn(new LocationSnapshot(dimension.locationId(), TENANT_A, type, "ACTIVE"));
    }

    /**
     * 构造库存余额快照。
     *
     * @param dimension 库存维度
     * @param onHand 实物数量
     * @param reserved 预留数量
     * @param version 余额版本
     * @return 库存余额
     */
    private InventoryBalance balance(InventoryDimension dimension, String onHand, String reserved, long version) {
        return new InventoryBalance(UUID.randomUUID(), TENANT_A, dimension, qty(onHand), qty(reserved), version,
                BUSINESS_TIME);
    }

    /**
     * 构造预留快照。
     *
     * @param id 预留 ID
     * @param reserved 原始预留数量
     * @param released 已释放数量
     * @param version 预留版本
     * @return 预留事实
     */
    private InventoryReservation reservation(UUID id, String reserved, String released, long version) {
        return new InventoryReservation(id, TENANT_A, "RES-" + id, "TEST", SOURCE_ID, null,
                qty(reserved), qty(released), released.equals("0") ? "Active" : "PartiallyReleased", version,
                BUSINESS_TIME, BUSINESS_TIME);
    }

    /**
     * 构造预留分配快照。
     *
     * @param id 分配 ID
     * @param reservationId 预留 ID
     * @param dimension 分配维度
     * @param allocated 分配数量
     * @param released 已释放数量
     * @param version 分配版本
     * @return 分配事实
     */
    private InventoryReservationAllocation allocation(UUID id, UUID reservationId, InventoryDimension dimension,
                                                       String allocated, String released, long version) {
        return new InventoryReservationAllocation(id, TENANT_A, reservationId, dimension, qty(allocated),
                qty(released), version, BUSINESS_TIME, BUSINESS_TIME);
    }

    /**
     * 把测试数量规范化为 NUMERIC(19,6) 的六位小数。
     *
     * @param value 数量文本
     * @return 六位小数数量
     */
    private BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
