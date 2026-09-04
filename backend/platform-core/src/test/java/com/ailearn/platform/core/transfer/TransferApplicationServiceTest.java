package com.ailearn.platform.core.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.transfer.application.TransferApplicationServiceImpl;
import com.ailearn.platform.core.transfer.domain.TransferLine;
import com.ailearn.platform.core.transfer.domain.TransferOrder;
import com.ailearn.platform.core.transfer.domain.TransferRepository;
import com.ailearn.platform.core.transfer.domain.TransferStatus;
import com.ailearn.platform.core.transfer.dto.TransferCreateRequest;
import com.ailearn.platform.core.transfer.dto.TransferLineRequest;
import com.ailearn.platform.core.transfer.dto.TransferView;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ValidationException;
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
 * 调拨应用服务纯单元测试。
 * <p>
 * 不连接数据库，验证 Draft -> Confirmed、库存端口边界、租户隔离和幂等行为。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TransferApplicationServiceTest {

    private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID FROM_LOCATION = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID TO_LOCATION = UUID.fromString("e0000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID LINE_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID TRANSACTION_ID = UUID.fromString("f0000000-0000-0000-0000-000000000003");
    private static final OffsetDateTime TIME = OffsetDateTime.of(2026, 9, 3, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String SESSION_ID = "jti-transfer-test";
    private static final String REQUEST_ID = "request-transfer-test";

    @Mock
    private TransferRepository repository;

    @Mock
    private InventoryCommandService inventoryCommandService;

    @Mock
    private WarehouseReferencePort warehouseReferencePort;

    @Mock
    private InventoryLocationPort inventoryLocationPort;

    private TransferApplicationServiceImpl service;

    /**
     * 设置可信租户、用户、会话和请求上下文。
     */
    @BeforeEach
    void setUpContext() {
        TenantContextHolder.setTenantId(TENANT_A);
        RequestContextHolder.getContext().setUserId(USER_ID);
        RequestContextHolder.getContext().setJti(SESSION_ID);
        RequestContextHolder.getContext().setRequestId(REQUEST_ID);
        service = new TransferApplicationServiceImpl(repository, inventoryCommandService);
    }

    /**
     * 清理线程上下文，避免测试间串租户。
     */
    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    /**
     * 创建调拨只保存草稿，不提前调用库存写端口。
     */
    @Test
    void createStartsAsDraftAndDoesNotMoveInventory() {
        when(repository.insert(any(TransferOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferView result = service.create(createRequest(), "transfer-create-1");

        assertEquals("Draft", result.getStatus());
        assertEquals("2.000000", result.getLines().getFirst().quantity());
        verify(repository).insert(any(TransferOrder.class));
        verifyNoInventoryMove();
    }

    /**
     * 来源与目标库位相同时拒绝创建。
     */
    @Test
    void sameSourceAndTargetIsRejected() {
        TransferCreateRequest request = createRequest();
        request.setToLocationId(FROM_LOCATION);

        assertThrows(ValidationException.class, () -> service.create(request, "transfer-invalid-1"));

        verify(repository, never()).insert(any(TransferOrder.class));
    }

    @Test
    void strictProductionConstructorRejectsInactiveWarehouseBeforeDraftInsert() {
        when(warehouseReferencePort.isActiveInTenant(TENANT_A, WAREHOUSE_ID)).thenReturn(false);
        TransferApplicationServiceImpl strictService = new TransferApplicationServiceImpl(repository,
                inventoryCommandService, new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper(), warehouseReferencePort, inventoryLocationPort);

        assertThrows(NotFoundException.class,
                () -> strictService.create(createRequest(), "transfer-masterdata-1"));
        verify(repository, never()).insert(any(TransferOrder.class));
    }

    /**
     * 确认只通过 InventoryCommandService.move，并在库存成功后推进调拨状态。
     */
    @Test
    void confirmMovesBothSidesAndTransitionsToConfirmed() {
        TransferOrder order = draftOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        when(repository.confirm(eq(TENANT_A), eq(ORDER_ID), eq(0L), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(inventoryCommandService.move(any(InventoryMoveCommand.class))).thenReturn(mutationResult());

        TransferView result = service.confirm(ORDER_ID, "transfer-confirm-1");

        assertEquals("Confirmed", result.getStatus());
        assertEquals(1, result.getTransactionIds().size());
        assertEquals(TRANSACTION_ID, result.getTransactionIds().getFirst());
        ArgumentCaptor<InventoryMoveCommand> captor = ArgumentCaptor.forClass(InventoryMoveCommand.class);
        verify(inventoryCommandService).move(captor.capture());
        InventoryMoveCommand command = captor.getValue();
        assertEquals(new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, FROM_LOCATION, "LOT-1"),
                command.fromDimension());
        assertEquals(new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, TO_LOCATION, "LOT-1"),
                command.toDimension());
        assertEquals("TRANSFER", command.metadata().transactionType());
        verify(repository).confirm(eq(TENANT_A), eq(ORDER_ID), eq(0L), eq(USER_ID), any(OffsetDateTime.class));
    }

    /**
     * 跨租户或不存在的调拨单不可见，也不得调用库存端口。
     */
    @Test
    void crossTenantOrderIsInvisible() {
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.confirm(ORDER_ID, "transfer-cross-tenant-1"));

        verify(inventoryCommandService, never()).move(any(InventoryMoveCommand.class));
    }

    /**
     * 已确认调拨不可再次确认。
     */
    @Test
    void confirmedOrderCannotBeConfirmedAgain() {
        TransferOrder order = new TransferOrder(ORDER_ID, TENANT_A, "TR-001", WAREHOUSE_ID, FROM_LOCATION,
                WAREHOUSE_ID, TO_LOCATION, TransferStatus.Confirmed, 1L, USER_ID, TIME, USER_ID, TIME,
                USER_ID, TIME, List.of(line()));
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> service.confirm(ORDER_ID, "transfer-confirmed-1"));

        verify(inventoryCommandService, never()).move(any(InventoryMoveCommand.class));
    }

    /**
     * 同一确认载荷重试命中幂等结果，不重复移动库存。
     */
    @Test
    void sameConfirmPayloadIsIdempotent() {
        TransferOrder order = draftOrder();
        when(repository.findById(TENANT_A, ORDER_ID)).thenReturn(Optional.of(order));
        when(repository.confirm(eq(TENANT_A), eq(ORDER_ID), eq(0L), eq(USER_ID), any(OffsetDateTime.class)))
                .thenReturn(true);
        when(inventoryCommandService.move(any(InventoryMoveCommand.class))).thenReturn(mutationResult());

        TransferView first = service.confirm(ORDER_ID, "transfer-repeat-1");
        TransferView replay = service.confirm(ORDER_ID, "transfer-repeat-1");

        assertEquals(first.getId(), replay.getId());
        assertEquals(first.getTransactionIds(), replay.getTransactionIds());
        verify(inventoryCommandService).move(any(InventoryMoveCommand.class));
        verify(repository).confirm(eq(TENANT_A), eq(ORDER_ID), eq(0L), eq(USER_ID), any(OffsetDateTime.class));
    }

    /**
     * 构造创建请求。
     */
    private TransferCreateRequest createRequest() {
        TransferCreateRequest request = new TransferCreateRequest();
        request.setTransferNo("TR-001");
        request.setFromWarehouseId(WAREHOUSE_ID);
        request.setFromLocationId(FROM_LOCATION);
        request.setToWarehouseId(WAREHOUSE_ID);
        request.setToLocationId(TO_LOCATION);
        TransferLineRequest line = new TransferLineRequest();
        line.setProductId(PRODUCT_ID);
        line.setLotNo("LOT-1");
        line.setUom("件");
        line.setQuantity("2");
        request.setLines(List.of(line));
        return request;
    }

    /**
     * 构造草稿聚合。
     */
    private TransferOrder draftOrder() {
        return new TransferOrder(ORDER_ID, TENANT_A, "TR-001", WAREHOUSE_ID, FROM_LOCATION,
                WAREHOUSE_ID, TO_LOCATION, TransferStatus.Draft, 0L, null, null, USER_ID, TIME,
                USER_ID, TIME, List.of(line()));
    }

    /**
     * 构造调拨明细。
     */
    private TransferLine line() {
        return new TransferLine(LINE_ID, TENANT_A, 1, PRODUCT_ID, "LOT-1", "件", qty("2"));
    }

    /**
     * 构造库存移动返回值。
     */
    private InventoryMutationResult mutationResult() {
        InventoryTransaction transaction = new InventoryTransaction(TRANSACTION_ID, TENANT_A, "INV-TR-1",
                "TRANSFER", "TRANSFER", ORDER_ID, LINE_ID,
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, FROM_LOCATION, "LOT-1"),
                new InventoryDimension(PRODUCT_ID, WAREHOUSE_ID, TO_LOCATION, "LOT-1"), qty("2"), TIME,
                USER_ID, SESSION_ID, REQUEST_ID, "inventory:move|transfer-line", "digest");
        return new InventoryMutationResult("MOVE", qty("2"), List.of(), null, List.of(),
                List.of(transaction), java.util.Set.of("MOVE"));
    }

    /**
     * 确认没有发生直接库存移动。
     */
    private void verifyNoInventoryMove() {
        verify(inventoryCommandService, never()).move(any(InventoryMoveCommand.class));
    }

    private BigDecimal qty(String value) {
        return new BigDecimal(value).setScale(6);
    }
}
