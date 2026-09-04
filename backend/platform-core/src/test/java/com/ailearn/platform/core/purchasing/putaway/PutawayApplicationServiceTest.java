package com.ailearn.platform.core.purchasing.putaway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.core.purchasing.putaway.application.PutawayApplicationService;
import com.ailearn.platform.core.purchasing.putaway.application.PutawayApplicationServiceImpl;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskFact;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskRepository;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayConfirmRequest;
import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.quality.domain.QualityReceiptFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptLineFact;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 上架应用服务 focused tests，验证上架只经库存 move 且限制库位类型。
 */
@ExtendWith(MockitoExtension.class)
class PutawayApplicationServiceTest {

    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID taskId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final UUID receiptId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final UUID lineId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private final UUID productId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private final UUID warehouseId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID receivingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID storageId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private PurchaseQualityRepository qualityRepository;
    @Mock private PurchasingReferencePort referencePort;
    @Mock private InventoryCommandService inventoryCommandService;
    private InMemoryPutawayRepository repository;
    private PutawayApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPutawayRepository();
        repository.tasks.put(taskId, task());
        service = new PutawayApplicationServiceImpl(repository, qualityRepository, referencePort,
                inventoryCommandService);
        RequestContextHolder.getContext().setTenantId(tenantId);
        RequestContextHolder.getContext().setUserId(userId);
        RequestContextHolder.getContext().setJti("putaway-test-jti");
        RequestContextHolder.getContext().setRequestId("putaway-test-request");
        when(qualityRepository.findReceipt(tenantId, receiptId, true)).thenReturn(Optional.of(receipt()));
        org.mockito.Mockito.lenient().when(referencePort.findActiveLocation(tenantId, receivingId)).thenReturn(Optional.of(
                new PurchasingLocationFact(receivingId, tenantId, warehouseId, "ReceivingStaging", "ACTIVE")));
        org.mockito.Mockito.lenient().when(referencePort.findActiveLocation(tenantId, storageId)).thenReturn(Optional.of(
                new PurchasingLocationFact(storageId, tenantId, warehouseId, "Storage", "ACTIVE")));
        org.mockito.Mockito.lenient().when(inventoryCommandService.move(any(InventoryMoveCommand.class))).thenReturn(mutation());
    }

    @Test
    void confirmsReceivingStagingToStorageWithoutIncreasingInventory() {
        PutawayConfirmRequest request = new PutawayConfirmRequest();
        request.setTaskId(taskId);
        request.setToLocationId(storageId);
        request.setPutawayQty("5");

        var result = service.confirm(taskId, request, "putaway-1");

        assertEquals("Confirmed", result.status());
        verify(inventoryCommandService).move(any(InventoryMoveCommand.class));
        assertEquals("Confirmed", repository.tasks.get(taskId).status());
    }

    @Test
    void rejectsWrongQuantityOrWrongTargetTypeBeforeInventoryMove() {
        PutawayConfirmRequest wrongQuantity = new PutawayConfirmRequest();
        wrongQuantity.setTaskId(taskId);
        wrongQuantity.setToLocationId(storageId);
        wrongQuantity.setPutawayQty("4");
        assertThrows(RuntimeException.class, () -> service.confirm(taskId, wrongQuantity, "putaway-qty-invalid"));

        UUID notStorage = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(referencePort.findActiveLocation(tenantId, notStorage)).thenReturn(Optional.of(
                new PurchasingLocationFact(notStorage, tenantId, warehouseId, "QualityHold", "ACTIVE")));
        PutawayConfirmRequest wrongTarget = new PutawayConfirmRequest();
        wrongTarget.setTaskId(taskId);
        wrongTarget.setToLocationId(notStorage);
        wrongTarget.setPutawayQty("5");
        assertThrows(RuntimeException.class, () -> service.confirm(taskId, wrongTarget, "putaway-target-invalid"));
    }

    private PutawayTaskFact task() {
        return new PutawayTaskFact(taskId, tenantId, "PUT-001", receiptId, lineId, productId, receivingId,
                storageId, warehouseId, new BigDecimal("5.000000"), "Pending", null, null, userId,
                OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC), null);
    }

    private QualityReceiptFact receipt() {
        return new QualityReceiptFact(receiptId, tenantId, UUID.randomUUID(), "PO-001",
                OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC), UUID.randomUUID(), "Confirmed",
                List.of(new QualityReceiptLineFact(lineId, UUID.randomUUID(), productId, "PCS",
                        new BigDecimal("5.000000"), "", warehouseId)));
    }

    private InventoryMutationResult mutation() {
        InventoryTransaction transaction = new InventoryTransaction(UUID.randomUUID(), tenantId, "INV-PUTAWAY",
                "PUTAWAY", "PUTAWAY_TASK", taskId, lineId, null, null, new BigDecimal("5.000000"),
                OffsetDateTime.of(2026, 9, 4, 10, 0, 0, 0, ZoneOffset.UTC), userId, "putaway-test-jti",
                "putaway-test-request", "inventory-putaway", "digest");
        return new InventoryMutationResult("MOVE", new BigDecimal("5.000000"), List.of(), null, List.of(),
                List.of(transaction), java.util.Set.of());
    }

    private final class InMemoryPutawayRepository implements PutawayTaskRepository {
        private final Map<UUID, PutawayTaskFact> tasks = new LinkedHashMap<>();
        @Override public Optional<UUID> findDefaultStorageLocation(UUID tenant, UUID warehouse) { return Optional.of(storageId); }
        @Override public PutawayTaskFact insert(PutawayTaskFact task) { tasks.put(task.id(), task); return task; }
        @Override public Optional<PutawayTaskFact> findById(UUID tenant, UUID id, boolean lock) { return Optional.ofNullable(tasks.get(id)); }
        @Override public PutawayTaskFact complete(PutawayTaskFact task, UUID operator, OffsetDateTime at, UUID tx) {
            PutawayTaskFact completed = new PutawayTaskFact(task.id(), task.tenantId(), task.taskNo(), task.purchaseReceiptId(),
                    task.purchaseReceiptLineId(), task.productId(), task.fromLocationId(), task.toLocationId(), task.warehouseId(),
                    task.putawayQty(), "Confirmed", operator, at, task.createdBy(), task.createdAt(), tx);
            tasks.put(task.id(), completed); return completed;
        }
        @Override public PutawayTaskFact updateTarget(PutawayTaskFact task, UUID target, UUID operator) { return task; }
        @Override public List<PutawayTaskFact> findPage(UUID tenant, String status, int page, int size) { return new ArrayList<>(tasks.values()); }
        @Override public long count(UUID tenant, String status) { return tasks.size(); }
    }
}
