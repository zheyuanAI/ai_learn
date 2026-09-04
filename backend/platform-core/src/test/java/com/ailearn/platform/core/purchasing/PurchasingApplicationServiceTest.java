package com.ailearn.platform.core.purchasing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderSourceFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.WorkOrderSourcePort;
import com.ailearn.platform.core.purchasing.application.PurchaseOrderApplicationService;
import com.ailearn.platform.core.purchasing.application.PurchasingApplicationServiceImpl;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrder;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPage;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPageQuery;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderRepository;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceipt;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderCompleteRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderLineRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderSaveRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderView;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptConfirmRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptLineRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptView;
import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.PurchasingProductFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 采购订单和到货验收应用服务 focused tests。
 * <p>
 * 使用内存采购 Repository 验证业务事务边界，库存以应用端口 mock 验证调用方向；不触碰开发数据库。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PurchasingApplicationServiceTest {

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID supplierId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final UUID productId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final UUID warehouseId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private final UUID holdLocationId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock
    private PurchasingReferencePort referencePort;
    @Mock
    private WorkOrderSourcePort workOrderSourcePort;
    @Mock
    private InventoryCommandService inventoryCommandService;

    private InMemoryPurchaseOrderRepository repository;
    private PurchaseOrderApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchaseOrderRepository();
        service = new PurchasingApplicationServiceImpl(repository, referencePort, workOrderSourcePort,
                inventoryCommandService);
        RequestContextHolder.getContext().setTenantId(tenantId);
        RequestContextHolder.getContext().setUserId(userId);
        RequestContextHolder.getContext().setJti("jti-purchasing-test");
        RequestContextHolder.getContext().setRequestId("request-purchasing-test");

        lenient().when(referencePort.isActiveSupplier(tenantId, supplierId)).thenReturn(true);
        lenient().when(referencePort.isActiveWarehouse(tenantId, warehouseId)).thenReturn(true);
        lenient().when(referencePort.findActiveProduct(tenantId, productId))
                .thenReturn(Optional.of(new PurchasingProductFact(productId, tenantId, "PCS", false)));
        lenient().when(referencePort.findActiveLocation(tenantId, holdLocationId))
                .thenReturn(Optional.of(new PurchasingLocationFact(holdLocationId, tenantId, warehouseId,
                        "QualityHold", "ACTIVE")));
        lenient().when(workOrderSourcePort.findActiveForProduct(any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(inventoryCommandService.increase(any(InventoryIncreaseCommand.class))).thenAnswer(invocation -> {
            InventoryIncreaseCommand command = invocation.getArgument(0, InventoryIncreaseCommand.class);
            return new InventoryMutationResult("INCREASE", command.quantity(), List.of(), null, List.of(),
                    List.of(new InventoryTransaction(UUID.randomUUID(), tenantId, "INV-RECEIPT", "RECEIPT",
                            command.metadata().sourceType(), command.metadata().sourceId(), command.metadata().sourceLineId(),
                            null, command.dimension(), command.quantity(), command.metadata().businessTime(), userId,
                            command.metadata().sessionId(), command.metadata().requestId(), command.metadata().idempotencyKey(),
                            command.metadata().payloadDigest())), java.util.Set.of());
        });
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void followsLifecycleAndManualCompletionWithoutInventoryFact() {
        PurchaseOrderView created = service.create(orderRequest("PO-001", "10"), "create-1");
        assertEquals("Draft", created.getStatus());

        PurchaseOrderView submitted = service.submit(created.getId(), "submit-1");
        assertEquals("Submitted", submitted.getStatus());
        PurchaseOrderView approved = service.approve(created.getId(), "approve-1");
        assertEquals("Approved", approved.getStatus());

        PurchaseOrderCompleteRequest completeRequest = new PurchaseOrderCompleteRequest();
        completeRequest.setCompletionReason("供应方交期取消，终止剩余待收");
        PurchaseOrderView completed = service.manuallyComplete(created.getId(), completeRequest, "complete-1");

        assertEquals("Completed", completed.getStatus());
        assertEquals("Manual", completed.getCompletionType());
        assertEquals("供应方交期取消，终止剩余待收", completed.getCompletionReason());
        assertEquals(userId, completed.getCompletedBy());
        assertEquals("jti-purchasing-test", completed.getCompletedSessionId());
        verifyNoInteractions(inventoryCommandService);
    }

    @Test
    void receivesOnlyAcceptedQuantityIntoQualityHoldAndUpdatesPending() {
        PurchaseOrderView approved = approvedOrder("PO-002", "10");
        PurchaseReceiptConfirmRequest request = receiptRequest(approved, "10", "2", "8", "外包装破损 2 件");

        PurchaseReceiptView receipt = service.confirmReceipt(UUID.randomUUID(), request, "receipt-1");

        assertEquals("Confirmed", receipt.getStatus());
        assertEquals("10.000000", receipt.getArrivalAcceptanceSummary().arrivedQty());
        assertEquals("2.000000", receipt.getArrivalAcceptanceSummary().rejectedQty());
        assertEquals("8.000000", receipt.getArrivalAcceptanceSummary().receivedQty());
        assertEquals("8.000000", receipt.getBalanceDeltaSummary().receivedQty());
        assertEquals(1, receipt.getInventoryTransactions().size());

        PurchaseOrder persisted = repository.orders.get(approved.getId());
        assertEquals("PartiallyReceived", persisted.status().name());
        assertEquals(new BigDecimal("8.000000"), persisted.lines().get(0).receivedQty());
        assertEquals(new BigDecimal("2.000000"), persisted.lines().get(0).pendingQty());

        ArgumentCaptor<InventoryIncreaseCommand> captor = ArgumentCaptor.forClass(InventoryIncreaseCommand.class);
        verify(inventoryCommandService, times(1)).increase(captor.capture());
        InventoryIncreaseCommand command = captor.getValue();
        assertEquals(new BigDecimal("8.000000"), command.quantity());
        assertEquals(productId, command.dimension().productId());
        assertEquals(warehouseId, command.dimension().warehouseId());
        assertEquals(holdLocationId, command.dimension().locationId());
        assertEquals("PURCHASE_RECEIPT", command.metadata().sourceType());
        assertEquals(receipt.getId(), command.metadata().sourceId());
    }

    @Test
    void allRejectedCreatesReceiptButDoesNotIncreaseInventoryOrReceivedTotal() {
        PurchaseOrderView approved = approvedOrder("PO-003", "10");
        PurchaseReceiptConfirmRequest request = receiptRequest(approved, "10", "10", "0", "型号错误");

        PurchaseReceiptView receipt = service.confirmReceipt(UUID.randomUUID(), request, "receipt-2");

        assertEquals("10.000000", receipt.getArrivalAcceptanceSummary().rejectedQty());
        assertEquals("0.000000", receipt.getArrivalAcceptanceSummary().receivedQty());
        assertEquals("0.000000", receipt.getBalanceDeltaSummary().receivedQty());
        assertEquals(false, receipt.getBalanceDeltaSummary().inventoryChanged());
        verifyNoInteractions(inventoryCommandService);
        assertEquals("Approved", repository.orders.get(approved.getId()).status().name());
        assertEquals(new BigDecimal("0.000000"), repository.orders.get(approved.getId()).lines().get(0).receivedQty());
    }

    @Test
    void fullyReceivingCompletesNormallyAndKeepsTrustedCompletionAudit() {
        PurchaseOrderView approved = approvedOrder("PO-003-NORMAL", "3");
        PurchaseReceiptConfirmRequest request = receiptRequest(approved, "3", "0", "3", null);

        service.confirmReceipt(UUID.randomUUID(), request, "receipt-normal");

        PurchaseOrder persisted = repository.orders.get(approved.getId());
        assertEquals("Completed", persisted.status().name());
        assertEquals("Normal", persisted.completionType().name());
        assertEquals(userId, persisted.completedBy());
        assertEquals("jti-purchasing-test", persisted.completedSessionId());
        assertEquals(new BigDecimal("3.000000"), persisted.lines().get(0).receivedQty());
        verify(inventoryCommandService, times(1)).increase(any(InventoryIncreaseCommand.class));
    }

    @Test
    void repeatedReceiptCommandReplaysWithoutSecondReceiptOrInventoryFact() {
        PurchaseOrderView approved = approvedOrder("PO-003-IDEMPOTENT", "3");
        UUID receiptId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PurchaseReceiptConfirmRequest request = receiptRequest(approved, "3", "1", "2", "数量争议");

        PurchaseReceiptView first = service.confirmReceipt(receiptId, request, "receipt-replay");
        PurchaseReceiptView replay = service.confirmReceipt(receiptId, request, "receipt-replay");

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, repository.receipts.size());
        verify(inventoryCommandService, times(1)).increase(any(InventoryIncreaseCommand.class));
    }

    @Test
    void rejectsInvalidRelationOrMissingReasonBeforeAnyFactIsWritten() {
        PurchaseOrderView approved = approvedOrder("PO-004", "10");
        PurchaseReceiptConfirmRequest relationError = receiptRequest(approved, "10", "1", "8", "数量争议");

        PurchasingException exception = assertThrows(PurchasingException.class,
                () -> service.confirmReceipt(UUID.randomUUID(), relationError, "receipt-3"));

        assertEquals(PurchasingErrorCode.PO_004.businessCode(), exception.getBusinessCode());
        assertEquals(0, repository.receipts.size());
        verifyNoInteractions(inventoryCommandService);

        PurchaseReceiptConfirmRequest missingReason = receiptRequest(approved, "10", "1", "9", " ");
        PurchasingException reasonException = assertThrows(PurchasingException.class,
                () -> service.confirmReceipt(UUID.randomUUID(), missingReason, "receipt-4"));
        assertEquals(PurchasingErrorCode.PO_005.businessCode(), reasonException.getBusinessCode());
        assertEquals(0, repository.receipts.size());
    }

    @Test
    void rejectsOverPendingAndCrossTenantWorkOrderSource() {
        PurchaseOrderView approved = approvedOrder("PO-005", "5");
        PurchaseReceiptConfirmRequest overPending = receiptRequest(approved, "6", "0", "6", null);
        PurchasingException overPendingException = assertThrows(PurchasingException.class,
                () -> service.confirmReceipt(UUID.randomUUID(), overPending, "receipt-5"));
        assertEquals(PurchasingErrorCode.PO_002.businessCode(), overPendingException.getBusinessCode());

        UUID workOrderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PurchaseOrderSaveRequest sourceRequest = orderRequest("PO-006", "2");
        sourceRequest.getLines().get(0).setSourceWorkOrderId(workOrderId);
        PurchasingException sourceException = assertThrows(PurchasingException.class,
                () -> service.create(sourceRequest, "create-source-invalid"));
        assertEquals(PurchasingErrorCode.PO_004.businessCode(), sourceException.getBusinessCode());

        when(workOrderSourcePort.findActiveForProduct(tenantId, workOrderId, productId))
                .thenReturn(Optional.of(new WorkOrderSourceFact(workOrderId, tenantId, productId,
                        "WO-001", new BigDecimal("2.000000"), WorkOrderStatus.Draft, false)));
        PurchaseOrderView sourceAccepted = service.create(sourceRequest, "create-source-valid");
        assertEquals(workOrderId, sourceAccepted.getLines().get(0).sourceWorkOrderId());
    }

    @Test
    void sameIdempotencyKeyReplaysAndPayloadConflictDoesNotInsertAgain() {
        PurchaseOrderSaveRequest request = orderRequest("PO-007", "3");
        PurchaseOrderView first = service.create(request, "same-key");
        PurchaseOrderView replay = service.create(request, "same-key");

        assertEquals(first.getId(), replay.getId());
        assertEquals(1, repository.orders.size());

        PurchaseOrderSaveRequest changed = orderRequest("PO-008", "3");
        PurchasingException exception = assertThrows(PurchasingException.class,
                () -> service.create(changed, "same-key"));
        assertEquals(PurchasingErrorCode.PO_001.businessCode(), exception.getBusinessCode());
        assertEquals(1, repository.orders.size());
    }

    @Test
    void draftUpdateRequiresVersionAndRejectsStaleVersion() {
        PurchaseOrderView draft = service.create(orderRequest("PO-009", "3"), "create-update");
        PurchaseOrderSaveRequest update = orderRequest("PO-009", "4");
        update.setVersion(draft.getVersion() + 1);

        PurchasingException exception = assertThrows(PurchasingException.class,
                () -> service.update(draft.getId(), update, "update-stale"));

        assertEquals(PurchasingErrorCode.PO_001.businessCode(), exception.getBusinessCode());
        assertEquals("Draft", repository.orders.get(draft.getId()).status().name());
        assertEquals(new BigDecimal("3.000000"), repository.orders.get(draft.getId()).lines().get(0).orderedQty());
    }

    private PurchaseOrderView approvedOrder(String poNo, String quantity) {
        PurchaseOrderView created = service.create(orderRequest(poNo, quantity), "create-" + poNo);
        service.submit(created.getId(), "submit-" + poNo);
        return service.approve(created.getId(), "approve-" + poNo);
    }

    private PurchaseOrderSaveRequest orderRequest(String poNo, String quantity) {
        PurchaseOrderSaveRequest request = new PurchaseOrderSaveRequest();
        request.setPoNo(poNo);
        request.setSupplierId(supplierId);
        request.setExpectedArrivalDate(java.time.LocalDate.of(2026, 9, 10));
        PurchaseOrderLineRequest line = new PurchaseOrderLineRequest();
        line.setLineNo(1);
        line.setProductId(productId);
        line.setUom("PCS");
        line.setOrderedQty(quantity);
        line.setTargetWarehouseId(warehouseId);
        request.setLines(new ArrayList<>(List.of(line)));
        return request;
    }

    private PurchaseReceiptConfirmRequest receiptRequest(PurchaseOrderView order, String arrived,
                                                          String rejected, String received, String reason) {
        PurchaseReceiptConfirmRequest request = new PurchaseReceiptConfirmRequest();
        request.setPurchaseOrderId(order.getId());
        request.setReceiptNo("PR-" + order.getPoNo());
        request.setReceiptTime(OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC));
        request.setQualityHoldLocationId(holdLocationId);
        PurchaseReceiptLineRequest line = new PurchaseReceiptLineRequest();
        line.setPurchaseOrderLineId(order.getLines().get(0).id());
        line.setProductId(productId);
        line.setUom("PCS");
        line.setArrivedQty(arrived);
        line.setRejectedQty(rejected);
        line.setReceivedQty(received);
        line.setRejectionReason(reason);
        request.setLines(new ArrayList<>(List.of(line)));
        return request;
    }

    /**
     * focused test 使用的事务内内存适配器；生产实现为 PostgresPurchaseOrderRepository。
     */
    private static final class InMemoryPurchaseOrderRepository implements PurchaseOrderRepository {
        private final Map<UUID, PurchaseOrder> orders = new LinkedHashMap<>();
        private final List<PurchaseReceipt> receipts = new ArrayList<>();

        @Override
        public PurchaseOrder insert(PurchaseOrder order) {
            orders.put(order.id(), order);
            return order;
        }

        @Override
        public Optional<PurchaseOrder> findById(UUID tenantId, UUID id) {
            return Optional.ofNullable(orders.get(id)).filter(order -> order.tenantId().equals(tenantId));
        }

        @Override
        public Optional<PurchaseOrder> findByIdForUpdate(UUID tenantId, UUID id) {
            return findById(tenantId, id);
        }

        @Override
        public PurchaseOrder updateDraft(PurchaseOrder order, long expectedVersion) {
            PurchaseOrder current = orders.get(order.id());
            if (current == null || current.version() != expectedVersion || current.status() != com.ailearn.platform.core.purchasing.domain.PurchaseOrderStatus.Draft) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "采购单版本已变化");
            }
            orders.put(order.id(), order);
            return order;
        }

        @Override
        public PurchaseOrder updateState(PurchaseOrder order, long expectedVersion) {
            PurchaseOrder current = orders.get(order.id());
            if (current == null || current.version() != expectedVersion) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "采购单版本已变化");
            }
            orders.put(order.id(), order);
            return order;
        }

        @Override
        public PurchaseReceipt insertReceipt(PurchaseReceipt receipt) {
            receipts.add(receipt);
            return receipt;
        }

        @Override
        public PurchaseOrderPage findPage(UUID tenantId, PurchaseOrderPageQuery query) {
            List<PurchaseOrder> records = orders.values().stream()
                    .filter(order -> order.tenantId().equals(tenantId))
                    .filter(order -> query.status() == null || order.status() == query.status())
                    .filter(order -> query.keyword() == null || order.poNo().contains(query.keyword()))
                    .toList();
            return new PurchaseOrderPage(records, records.size(), query.page(), query.size());
        }
    }
}
