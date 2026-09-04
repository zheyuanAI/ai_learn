package com.ailearn.platform.core.purchasing.putaway.application;

import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskFact;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskRepository;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayConfirmRequest;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayTaskPageView;
import com.ailearn.platform.core.purchasing.putaway.dto.PutawayTaskView;
import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.quality.domain.QualityReceiptFact;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ForbiddenException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上架应用服务；只允许从 ReceivingStaging 通过 InventoryCommandService.move 移动到 Storage。
 */
@Service
public class PutawayApplicationServiceImpl implements PutawayApplicationService {

    private static final int SCALE = 6;
    private final PutawayTaskRepository repository;
    private final PurchaseQualityRepository qualityRepository;
    private final PurchasingReferencePort referencePort;
    private final InventoryCommandService inventoryCommandService;
    private final PutawayIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 提供纯单元测试使用的默认构造器。
     */
    public PutawayApplicationServiceImpl(PutawayTaskRepository repository,
                                         PurchaseQualityRepository qualityRepository,
                                         PurchasingReferencePort referencePort,
                                         InventoryCommandService inventoryCommandService) {
        this(repository, qualityRepository, referencePort, inventoryCommandService,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建可替换幂等存储的上架应用服务。
     */
    @Autowired
    public PutawayApplicationServiceImpl(PutawayTaskRepository repository,
                                         PurchaseQualityRepository qualityRepository,
                                         PurchasingReferencePort referencePort,
                                         InventoryCommandService inventoryCommandService,
                                         IdempotencyStorage storage,
                                         ObjectMapper objectMapper) {
        this.repository = repository;
        this.qualityRepository = qualityRepository;
        this.referencePort = referencePort;
        this.inventoryCommandService = inventoryCommandService;
        this.objectMapper = objectMapper;
        this.idempotencyExecutor = new PutawayIdempotencyExecutor(storage, objectMapper);
    }

    /**
     * 确认上架；数量必须与任务数量一致，避免 V3 没有剩余数量列时伪造部分完成状态。
     * 入参：上架任务、目标 Storage 和幂等键；出参：已确认任务；流程：锁任务 -> 校验收货来源 -> 调 move -> 写确认。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:putaway:confirm')")
    public PutawayTaskView confirm(UUID taskId, PutawayConfirmRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (request == null || (request.getTaskId() != null && !taskId.equals(request.getTaskId()))) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "上架任务标识不能为空且必须一致");
        }
        return idempotencyExecutor.execute("putaway:confirm", actor.tenantId(), idempotencyKey,
                digest("putaway-confirm", List.of(taskId, request)), PutawayTaskView.class,
                () -> confirmInternal(taskId, request, actor));
    }

    /**
     * 查询当前租户上架任务。
     */
    @Override
    @PreAuthorize("hasAuthority('pur:receipt:view')")
    public PutawayTaskPageView page(String status, int page, int size) {
        if (page < 1 || size < 1 || size > 200) throw new ValidationException("分页参数不合法");
        UUID tenantId = TenantContextHolder.requireTenantId();
        List<PutawayTaskView> records = repository.findPage(tenantId, status, page, size).stream()
                .map(PutawayTaskView::of).toList();
        return new PutawayTaskPageView(records, repository.count(tenantId, status), page, size);
    }

    private PutawayTaskView confirmInternal(UUID taskId, PutawayConfirmRequest request, Actor actor) {
        PutawayTaskFact task = repository.findById(actor.tenantId(), taskId, true)
                .orElseThrow(() -> new NotFoundException("上架任务"));
        if (!"Pending".equals(task.status()) && !"Processing".equals(task.status())) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "上架任务状态不允许确认");
        }
        QualityReceiptFact receipt = qualityRepository.findReceipt(actor.tenantId(), task.purchaseReceiptId(), true)
                .orElseThrow(() -> new NotFoundException("采购收货单"));
        var line = receipt.lines().stream().filter(item -> item.id().equals(task.purchaseReceiptLineId())
                && item.productId().equals(task.productId())).findFirst().orElseThrow(() ->
                new PurchasingException(PurchasingErrorCode.PO_004, "上架任务来源收货明细不存在"));
        if (!"Confirmed".equals(receipt.status())) {
            throw new PurchasingException(PurchasingErrorCode.PO_003, "收货单未确认，不能上架");
        }
        // 放行任务的 from_location 必须是 ReceivingStaging；此处拒绝历史脏任务。
        requireLocation(actor.tenantId(), task.fromLocationId(), task.warehouseId(), "ReceivingStaging");
        BigDecimal quantity = decimal(request.getPutawayQty());
        if (quantity.compareTo(task.putawayQty()) != 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_002, "本次上架数量必须等于任务数量");
        }
        UUID targetId = request.getToLocationId();
        if (targetId == null) throw new PurchasingException(PurchasingErrorCode.PO_004, "上架目标库位不能为空");
        PurchasingLocationFact target = requireLocation(actor.tenantId(), targetId, task.warehouseId(), "Storage");
        PutawayTaskFact effectiveTask = targetId.equals(task.toLocationId()) ? task
                : repository.updateTarget(task, target.id(), actor.userId());
        InventoryCommandMetadata metadata = new InventoryCommandMetadata(actor.tenantId(), actor.userId(),
                actor.sessionId(), actor.requestId(), "putaway-task-" + task.id(),
                digest("inventory-putaway", List.of(task.id(), effectiveTask.toLocationId(), quantity)),
                "PUTAWAY_TASK", task.id(), task.purchaseReceiptLineId(), "PUTAWAY", receipt.receiptTime());
        InventoryMutationResult mutation = inventoryCommandService.move(new InventoryMoveCommand(metadata,
                new InventoryDimension(task.productId(), task.warehouseId(), task.fromLocationId(), line.lotNo()),
                new InventoryDimension(task.productId(), task.warehouseId(), effectiveTask.toLocationId(), line.lotNo()),
                quantity));
        if (mutation == null || mutation.transactions().isEmpty()) {
            throw new ServiceUnavailableException("上架库存移动结果为空");
        }
        return PutawayTaskView.of(repository.complete(effectiveTask, actor.userId(), now(),
                mutation.transactions().get(0).id()));
    }

    private PurchasingLocationFact requireLocation(UUID tenantId, UUID locationId, UUID warehouseId, String type) {
        PurchasingLocationFact location = referencePort.findActiveLocation(tenantId, locationId)
                .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004, "上架库位不存在或未启用"));
        if (!type.equals(location.type()) || !warehouseId.equals(location.warehouseId())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "上架库位类型或所属仓库不合法");
        }
        return location;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.trim().isBlank()) throw new PurchasingException(PurchasingErrorCode.PO_004, "putawayQty 不能为空");
        try {
            BigDecimal result = new BigDecimal(value.trim());
            if (result.signum() <= 0 || result.scale() > SCALE) throw new PurchasingException(PurchasingErrorCode.PO_004, "putawayQty 不合法");
            return result.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException exception) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "putawayQty 格式不正确");
        }
    }

    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String session = UserContextHolder.getSessionId();
        String request = RequestContextHolder.getRequestId();
        if (session == null || session.isBlank() || request == null || request.isBlank()) {
            throw new ForbiddenException("缺失可信会话或请求上下文");
        }
        return new Actor(tenantId, userId, session, request);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ValidationException("Idempotency-Key 不合法");
    }

    private String digest(String operation, Object payload) {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("operation", operation); body.put("payload", payload);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ServiceUnavailableException("上架幂等载荷摘要生成失败", exception);
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) { }
}
