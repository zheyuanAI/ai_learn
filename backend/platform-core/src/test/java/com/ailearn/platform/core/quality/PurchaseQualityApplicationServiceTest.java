package com.ailearn.platform.core.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.quality.application.PurchaseQualityApplicationService;
import com.ailearn.platform.core.quality.application.PurchaseQualityApplicationServiceImpl;
import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.quality.domain.QualityDispositionFact;
import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import com.ailearn.platform.core.quality.domain.QualityInspectionFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptLineFact;
import com.ailearn.platform.core.quality.dto.QualityDispositionConfirmRequest;
import com.ailearn.platform.core.quality.dto.QualityDispositionRequest;
import com.ailearn.platform.core.quality.dto.QualityInspectionRequest;
import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskFact;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskRepository;
import com.ailearn.platform.shared.context.RequestContextHolder;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 采购到货质量与处置 focused tests；使用内存事实端口并以 mock 验证库存唯一写入口。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseQualityApplicationServiceTest {

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID receiptId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final UUID receiptLineId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final UUID orderId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private final UUID orderLineId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private final UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID warehouseId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID qualityHoldId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID receivingId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID storageId = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private PurchasingReferencePort referencePort;
    @Mock
    private InventoryCommandService inventoryCommandService;

    private InMemoryQualityRepository repository;
    private InMemoryPutawayTaskRepository putawayRepository;
    private PurchaseQualityApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryQualityRepository();
        putawayRepository = new InMemoryPutawayTaskRepository();
        service = new PurchaseQualityApplicationServiceImpl(repository, referencePort, inventoryCommandService,
                putawayRepository);
        RequestContextHolder.getContext().setTenantId(tenantId);
        RequestContextHolder.getContext().setUserId(userId);
        RequestContextHolder.getContext().setJti("quality-test-jti");
        RequestContextHolder.getContext().setRequestId("quality-test-request");
        org.mockito.Mockito.lenient().when(referencePort.findActiveLocation(tenantId, receivingId))
                .thenReturn(Optional.of(new PurchasingLocationFact(receivingId, tenantId, warehouseId,
                        "ReceivingStaging", "ACTIVE")));
        org.mockito.Mockito.lenient().when(referencePort.findActiveLocation(tenantId, storageId))
                .thenReturn(Optional.of(new PurchasingLocationFact(storageId, tenantId, warehouseId,
                        "Storage", "ACTIVE")));
        org.mockito.Mockito.lenient().when(inventoryCommandService.move(any(InventoryMoveCommand.class)))
                .thenReturn(mutation("QUALITY_RELEASE"));
        org.mockito.Mockito.lenient().when(inventoryCommandService.decrease(any()))
                .thenReturn(mutation("QUALITY_SCRAP"));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void inspectionQuantityRelationFailsBeforeWritingOrInventory() {
        QualityInspectionRequest request = inspection("10", "7", "2");

        PurchasingException exception = assertThrows(PurchasingException.class,
                () -> service.inspect(receiptId, request, "inspect-invalid"));

        assertEquals(PurchasingErrorCode.PO_004.businessCode(), exception.getBusinessCode());
        assertEquals(0, repository.inspections.size());
        verifyNoInteractions(inventoryCommandService);
    }

    @Test
    void inspectionOnlyWritesQualityFactAndCumulativeLimitIsEnforced() {
        QualityInspectionRequest first = inspection("6", "5", "1");
        var firstView = service.inspect(receiptId, first, "inspect-1");

        assertEquals("PendingDecision", firstView.status());
        assertEquals(1, repository.inspections.size());
        verifyNoInteractions(inventoryCommandService);

        QualityInspectionRequest over = inspection("5", "5", "0");
        PurchasingException exception = assertThrows(PurchasingException.class,
                () -> service.inspect(receiptId, over, "inspect-2"));
        assertEquals(PurchasingErrorCode.PO_002.businessCode(), exception.getBusinessCode());
        assertEquals(1, repository.inspections.size());
    }

    @Test
    void releaseDecisionIsPendingExecutionAndConfirmMovesOnlyAfterWarehouseAction() {
        var inspection = service.inspect(receiptId, inspection("10", "8", "2"), "inspect-release");
        QualityDispositionRequest request = disposition(inspection.id(), QualityDispositionType.Release, "8", null);

        var pending = service.release(receiptId, request, "decide-release");
        assertEquals("PendingExecution", pending.status());
        verifyNoInteractions(inventoryCommandService);

        QualityDispositionConfirmRequest confirm = new QualityDispositionConfirmRequest();
        confirm.setDispositionId(pending.id());
        confirm.setToLocationId(receivingId);
        confirm.setPutawayTargetLocationId(storageId);
        var completed = service.confirmDisposition(pending.id(), confirm, "execute-release");

        assertEquals("Completed", completed.status());
        assertEquals(QualityDispositionType.Release, completed.dispositionType());
        assertEquals(1, putawayRepository.tasks.size());
        verify(inventoryCommandService).move(any(InventoryMoveCommand.class));
    }

    @Test
    void scrapRequiresReasonAndWarehouseExecutionCallsDecrease() {
        var inspection = service.inspect(receiptId, inspection("10", "8", "2"), "inspect-scrap");
        QualityDispositionRequest missingReason = disposition(inspection.id(), QualityDispositionType.Scrap, "1", " ");
        assertThrows(PurchasingException.class, () -> service.scrap(receiptId, missingReason, "scrap-invalid"));

        var pending = service.scrap(receiptId, disposition(inspection.id(), QualityDispositionType.Scrap, "2", "尺寸超差"),
                "decide-scrap");
        assertEquals("PendingExecution", pending.status());
        verifyNoInteractions(inventoryCommandService);

        QualityDispositionConfirmRequest confirm = new QualityDispositionConfirmRequest();
        confirm.setDispositionId(pending.id());
        var completed = service.confirmDisposition(pending.id(), confirm, "execute-scrap");
        assertEquals("Completed", completed.status());
        verify(inventoryCommandService).decrease(any());
    }

    @Test
    void returnDecisionAlsoWaitsForWarehouseExecutionBeforeDecrease() {
        var inspection = service.inspect(receiptId, inspection("10", "8", "2"), "inspect-return");
        var pending = service.returnToSupplier(receiptId,
                disposition(inspection.id(), QualityDispositionType.Return, "1", "供应方补发"), "decide-return");
        assertEquals("PendingExecution", pending.status());
        verifyNoInteractions(inventoryCommandService);

        QualityDispositionConfirmRequest confirm = new QualityDispositionConfirmRequest();
        confirm.setDispositionId(pending.id());
        var completed = service.confirmDisposition(pending.id(), confirm, "execute-return");
        assertEquals("Completed", completed.status());
        verify(inventoryCommandService).decrease(any());
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutDuplicateQualityFact() {
        QualityInspectionRequest request = inspection("10", "10", "0");
        var first = service.inspect(receiptId, request, "same-quality-key");
        var replay = service.inspect(receiptId, request, "same-quality-key");

        assertEquals(first.id(), replay.id());
        assertEquals(1, repository.inspections.size());
    }

    private QualityInspectionRequest inspection(String inspected, String qualified, String unqualified) {
        QualityInspectionRequest request = new QualityInspectionRequest();
        request.setPurchaseOrderId(orderId);
        request.setPurchaseReceiptId(receiptId);
        request.setPurchaseReceiptLineId(receiptLineId);
        request.setProductId(productId);
        request.setInspectedQty(inspected);
        request.setQualifiedQty(qualified);
        request.setUnqualifiedQty(unqualified);
        request.setUnqualifiedReason(new BigDecimal(unqualified).signum() > 0 ? "尺寸异常" : null);
        return request;
    }

    private QualityDispositionRequest disposition(UUID inspectionId, QualityDispositionType type,
                                                  String quantity, String reason) {
        QualityDispositionRequest request = new QualityDispositionRequest();
        request.setInspectionId(inspectionId);
        request.setDispositionType(type);
        request.setDispositionQty(quantity);
        request.setReason(reason);
        return request;
    }

    private InventoryMutationResult mutation(String transactionType) {
        InventoryTransaction transaction = new InventoryTransaction(UUID.randomUUID(), tenantId, "INV-Q",
                transactionType, "PURCHASE_QUALITY_DISPOSITION", UUID.randomUUID(), receiptLineId, null, null,
                new BigDecimal("1.000000"), OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC), userId,
                "quality-test-jti", "quality-test-request", "inventory-key", "digest");
        return new InventoryMutationResult(transactionType, new BigDecimal("1.000000"), List.of(), null, List.of(),
                List.of(transaction), java.util.Set.of());
    }

    private final class InMemoryQualityRepository implements PurchaseQualityRepository {
        private final Map<UUID, QualityInspectionFact> inspections = new LinkedHashMap<>();
        private final Map<UUID, QualityDispositionFact> dispositions = new LinkedHashMap<>();

        @Override
        public Optional<QualityReceiptFact> findReceipt(UUID tenant, UUID id, boolean lock) {
            return id.equals(receiptId) && tenant.equals(tenantId) ? Optional.of(receipt()) : Optional.empty();
        }

        @Override
        public Optional<QualityInspectionFact> findInspection(UUID tenant, UUID id, boolean lock) {
            return Optional.ofNullable(inspections.get(id)).filter(item -> tenant.equals(item.tenantId()));
        }

        @Override
        public List<QualityInspectionFact> findInspectionsByLine(UUID tenant, UUID line, boolean lock) {
            return inspections.values().stream().filter(item -> tenant.equals(item.tenantId())
                    && line.equals(item.purchaseReceiptLineId())).toList();
        }

        @Override
        public QualityInspectionFact insertInspection(QualityInspectionFact inspection) {
            inspections.put(inspection.id(), inspection);
            return inspection;
        }

        @Override
        public List<QualityDispositionFact> findDispositionsByInspection(UUID tenant, UUID id, boolean lock) {
            return dispositions.values().stream().filter(item -> tenant.equals(item.tenantId())
                    && id.equals(item.inspectionId())).toList();
        }

        @Override
        public Optional<QualityDispositionFact> findDisposition(UUID tenant, UUID id, boolean lock) {
            return Optional.ofNullable(dispositions.get(id)).filter(item -> tenant.equals(item.tenantId()));
        }

        @Override
        public QualityDispositionFact insertDisposition(QualityDispositionFact disposition) {
            dispositions.put(disposition.id(), disposition);
            return disposition;
        }

        @Override
        public QualityDispositionFact completeDisposition(QualityDispositionFact disposition, UUID operator,
                                                          OffsetDateTime at, UUID transactionId) {
            QualityDispositionFact completed = new QualityDispositionFact(disposition.id(), disposition.tenantId(),
                    disposition.inspectionId(), disposition.type(), disposition.quantity(), disposition.reason(),
                    "Completed", disposition.decidedBy(), disposition.decidedAt(), operator, at, transactionId,
                    disposition.createdAt());
            dispositions.put(completed.id(), completed);
            return completed;
        }

        @Override public List<QualityInspectionFact> listInspections(UUID tenant) { return new ArrayList<>(inspections.values()); }
        @Override public List<QualityDispositionFact> listDispositions(UUID tenant) { return new ArrayList<>(dispositions.values()); }

        private QualityReceiptFact receipt() {
            return new QualityReceiptFact(receiptId, tenantId, orderId, "PO-QA-001",
                    OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC), qualityHoldId, "Confirmed",
                    List.of(new QualityReceiptLineFact(receiptLineId, orderLineId, productId, "PCS",
                            new BigDecimal("10.000000"), "", warehouseId)));
        }
    }

    private final class InMemoryPutawayTaskRepository implements PutawayTaskRepository {
        private final Map<UUID, PutawayTaskFact> tasks = new LinkedHashMap<>();
        @Override public Optional<UUID> findDefaultStorageLocation(UUID tenant, UUID warehouse) { return Optional.of(storageId); }
        @Override public PutawayTaskFact insert(PutawayTaskFact task) { tasks.put(task.id(), task); return task; }
        @Override public Optional<PutawayTaskFact> findById(UUID tenant, UUID id, boolean lock) { return Optional.ofNullable(tasks.get(id)); }
        @Override public PutawayTaskFact complete(PutawayTaskFact task, UUID operator, OffsetDateTime at, UUID tx) {
            PutawayTaskFact completed = new PutawayTaskFact(task.id(), task.tenantId(), task.taskNo(), task.purchaseReceiptId(),
                    task.purchaseReceiptLineId(), task.productId(), task.fromLocationId(), task.toLocationId(), task.warehouseId(),
                    task.putawayQty(), "Confirmed", operator, at, task.createdBy(), task.createdAt(), tx);
            tasks.put(completed.id(), completed); return completed;
        }
        @Override public PutawayTaskFact updateTarget(PutawayTaskFact task, UUID target, UUID operator) { return task; }
        @Override public List<PutawayTaskFact> findPage(UUID tenant, String status, int page, int size) { return new ArrayList<>(tasks.values()); }
        @Override public long count(UUID tenant, String status) { return tasks.size(); }
    }
}
