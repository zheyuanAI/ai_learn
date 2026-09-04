package com.ailearn.platform.core.quality.application;

import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryDecreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.quality.domain.QualityDispositionFact;
import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import com.ailearn.platform.core.quality.domain.QualityInspectionFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptLineFact;
import com.ailearn.platform.core.quality.dto.QualityDispositionConfirmRequest;
import com.ailearn.platform.core.quality.dto.QualityDispositionRequest;
import com.ailearn.platform.core.quality.dto.QualityDispositionView;
import com.ailearn.platform.core.quality.dto.QualityInspectionRequest;
import com.ailearn.platform.core.quality.dto.QualityInspectionView;
import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskFact;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskRepository;
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
 * 采购到货质量应用服务。
 * <p>
 * 检验与业务决定只写质量事实；仓库确认时才通过 InventoryCommandService 形成移位或扣减事实，
 * 同一事务内完成质量状态、上架任务与库存流水关联。禁止本服务注入库存 Mapper。
 * </p>
 */
@Service
public class PurchaseQualityApplicationServiceImpl implements PurchaseQualityApplicationService {

    private static final int SCALE = 6;
    private static final int INTEGER_DIGITS = 13;

    private final PurchaseQualityRepository repository;
    private final PurchasingReferencePort referencePort;
    private final InventoryCommandService inventoryCommandService;
    private final PutawayTaskRepository putawayTaskRepository;
    private final QualityIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 提供纯单元测试使用的默认构造器。
     */
    public PurchaseQualityApplicationServiceImpl(PurchaseQualityRepository repository,
                                                 PurchasingReferencePort referencePort,
                                                 InventoryCommandService inventoryCommandService,
                                                 PutawayTaskRepository putawayTaskRepository) {
        this(repository, referencePort, inventoryCommandService, putawayTaskRepository,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建质量应用服务。
     * 入参：质量事实端口、主数据只读端口、库存唯一写端口、上架任务端口和共享幂等依赖；
     * 出参：可被 Spring 管理的质量应用服务；流程：保存依赖，命令执行时由可信上下文完成租户与审计校验。
     */
    @Autowired
    public PurchaseQualityApplicationServiceImpl(PurchaseQualityRepository repository,
                                                 PurchasingReferencePort referencePort,
                                                 InventoryCommandService inventoryCommandService,
                                                 PutawayTaskRepository putawayTaskRepository,
                                                 IdempotencyStorage storage,
                                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.referencePort = referencePort;
        this.inventoryCommandService = inventoryCommandService;
        this.putawayTaskRepository = putawayTaskRepository;
        this.objectMapper = objectMapper;
        this.idempotencyExecutor = new QualityIdempotencyExecutor(storage, objectMapper);
    }

    /**
     * 记录采购到货质检事实，不调用库存服务。
     * 入参：收货单、检验数量关系和检验说明；出参：质检事实；流程：锁收货明细 -> 校验累计上限 -> 写质量事实。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:quality:inspect')")
    public QualityInspectionView inspect(UUID receiptId, QualityInspectionRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateInspectionRequest(receiptId, request);
        return idempotencyExecutor.execute("quality:inspection", actor.tenantId(), idempotencyKey,
                digest("inspection", request), QualityInspectionView.class,
                () -> inspectInternal(receiptId, request, actor));
    }

    /**
     * 对质检合格数量下达放行决定，结果停留在 PendingExecution。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:quality:release')")
    public QualityDispositionView release(UUID receiptId, QualityDispositionRequest request, String idempotencyKey) {
        return decide(receiptId, request, QualityDispositionType.Release, idempotencyKey);
    }

    /**
     * 对质检不合格数量下达退回供应方决定，结果停留在 PendingExecution。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:quality:return')")
    public QualityDispositionView returnToSupplier(UUID receiptId, QualityDispositionRequest request,
                                                   String idempotencyKey) {
        return decide(receiptId, request, QualityDispositionType.Return, idempotencyKey);
    }

    /**
     * 对质检不合格数量下达报废决定，结果停留在 PendingExecution。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:quality:scrap')")
    public QualityDispositionView scrap(UUID receiptId, QualityDispositionRequest request, String idempotencyKey) {
        return decide(receiptId, request, QualityDispositionType.Scrap, idempotencyKey);
    }

    /**
     * 仓库执行质量处置；放行移动 QH -> RS，退回/报废从 QH 扣减实物。
     * 入参：待执行处置和放行目标库位；出参：Completed 处置；流程：锁处置 -> 调库存唯一写端口 -> 写执行审计。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:disposition:confirm')")
    public QualityDispositionView confirmDisposition(UUID dispositionId,
                                                     QualityDispositionConfirmRequest request,
                                                     String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (request != null && request.getDispositionId() != null
                && !dispositionId.equals(request.getDispositionId())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "处置 ID 与路径参数不一致");
        }
        return idempotencyExecutor.execute("quality:disposition:confirm", actor.tenantId(), idempotencyKey,
                digest("disposition-confirm", List.of(dispositionId, request)), QualityDispositionView.class,
                () -> confirmInternal(dispositionId, request, actor));
    }

    /**
     * 查询当前租户质检事实。
     */
    @Override
    @PreAuthorize("hasAuthority('pur:receipt:view')")
    public List<QualityInspectionView> listInspections() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return repository.listInspections(tenantId).stream().map(this::inspectionView).toList();
    }

    /**
     * 查询当前租户质量处置事实。
     */
    @Override
    @PreAuthorize("hasAuthority('pur:receipt:view')")
    public List<QualityDispositionView> listDispositions() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return repository.listDispositions(tenantId).stream().map(this::dispositionView).toList();
    }

    private QualityInspectionView inspectInternal(UUID receiptId, QualityInspectionRequest request, Actor actor) {
        QualityReceiptFact receipt = requiredReceipt(actor.tenantId(), receiptId, true);
        if (!"Confirmed".equals(receipt.status()) || !receiptId.equals(request.getPurchaseReceiptId())
                || !receipt.purchaseOrderId().equals(request.getPurchaseOrderId())) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "只有已确认收货单允许质检");
        }
        QualityReceiptLineFact line = requiredLine(receipt, request.getPurchaseReceiptLineId(), request.getProductId());
        BigDecimal inspected = decimal(request.getInspectedQty(), "inspectedQty", true);
        BigDecimal qualified = decimal(request.getQualifiedQty(), "qualifiedQty", false);
        BigDecimal unqualified = decimal(request.getUnqualifiedQty(), "unqualifiedQty", false);
        if (inspected.compareTo(qualified.add(unqualified)) != 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_004,
                    "inspectedQty 必须等于 qualifiedQty + unqualifiedQty");
        }
        if (inspected.compareTo(line.receivedQty()) > 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_002, "累计质检数量超过实际接收数量");
        }
        BigDecimal alreadyInspected = repository.findInspectionsByLine(actor.tenantId(), line.id(), true).stream()
                .map(QualityInspectionFact::inspectedQty)
                .reduce(BigDecimal.ZERO.setScale(SCALE), BigDecimal::add);
        if (alreadyInspected.add(inspected).compareTo(line.receivedQty()) > 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_002, "累计质检数量超过实际接收数量");
        }
        if (unqualified.signum() > 0 && blank(request.getUnqualifiedReason())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "不合格数量大于 0 时必须填写不合格原因");
        }
        String note = joinNote(request.getUnqualifiedReason(), request.getInspectionRemark());
        QualityInspectionFact fact = new QualityInspectionFact(UUID.randomUUID(), actor.tenantId(), receipt.id(),
                line.id(), line.productId(), inspected, qualified, unqualified, note, "PendingDecision",
                actor.userId(), now(), now());
        return inspectionView(repository.insertInspection(fact));
    }

    private QualityDispositionView decide(UUID receiptId, QualityDispositionRequest request,
                                          QualityDispositionType expectedType, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (request == null || request.getInspectionId() == null || request.getDispositionType() != expectedType) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "质量处置类型或质检事实不能为空");
        }
        if (expectedType != QualityDispositionType.Release && blank(request.getReason())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "退回或报废必须填写处置原因");
        }
        return idempotencyExecutor.execute("quality:disposition:" + expectedType, actor.tenantId(), idempotencyKey,
                digest("disposition-" + expectedType, List.of(receiptId, request)), QualityDispositionView.class,
                () -> decideInternal(receiptId, request, expectedType, actor));
    }

    private QualityDispositionView decideInternal(UUID receiptId, QualityDispositionRequest request,
                                                   QualityDispositionType type, Actor actor) {
        QualityInspectionFact inspection = requiredInspection(actor.tenantId(), request.getInspectionId(), true);
        QualityReceiptFact receipt = requiredReceipt(actor.tenantId(), inspection.purchaseReceiptId(), true);
        if (!receipt.id().equals(receiptId)) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "质检事实不属于当前收货单");
        }
        BigDecimal quantity = decimal(request.getDispositionQty(), "dispositionQty", true);
        List<QualityDispositionFact> existing = repository.findDispositionsByInspection(
                actor.tenantId(), inspection.id(), true);
        BigDecimal release = sum(existing, QualityDispositionType.Release);
        BigDecimal nonconforming = sum(existing, QualityDispositionType.Return)
                .add(sum(existing, QualityDispositionType.Scrap));
        BigDecimal limit = type == QualityDispositionType.Release
                ? inspection.qualifiedQty().subtract(release)
                : inspection.unqualifiedQty().subtract(nonconforming);
        if (quantity.compareTo(limit) > 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_002, "质量处置数量超过未处置数量");
        }
        QualityDispositionFact fact = new QualityDispositionFact(UUID.randomUUID(), actor.tenantId(), inspection.id(),
                type, quantity, normalize(request.getReason()), "PendingExecution", actor.userId(), now(), null,
                null, null, now());
        return dispositionView(repository.insertDisposition(fact));
    }

    private QualityDispositionView confirmInternal(UUID dispositionId, QualityDispositionConfirmRequest request,
                                                   Actor actor) {
        QualityDispositionFact disposition = repository.findDisposition(actor.tenantId(), dispositionId, true)
                .orElseThrow(() -> new NotFoundException("质量处置"));
        if (!"PendingExecution".equals(disposition.status())) {
            throw new PurchasingException(PurchasingErrorCode.PO_006, "质量处置不是待执行状态");
        }
        QualityInspectionFact inspection = requiredInspection(actor.tenantId(), disposition.inspectionId(), true);
        QualityReceiptFact receipt = requiredReceipt(actor.tenantId(), inspection.purchaseReceiptId(), true);
        QualityReceiptLineFact line = requiredLine(receipt, inspection.purchaseReceiptLineId(), inspection.productId());
        InventoryMutationResult mutation;
        UUID targetLocationId = null;
        if (disposition.type() == QualityDispositionType.Release) {
            targetLocationId = requiredReceivingStaging(actor.tenantId(), request == null ? null : request.getToLocationId(),
                    line.targetWarehouseId());
            InventoryCommandMetadata metadata = metadata(actor, disposition, line, "QUALITY_RELEASE", receipt.receiptTime());
            mutation = inventoryCommandService.move(new InventoryMoveCommand(metadata,
                    new InventoryDimension(line.productId(), line.targetWarehouseId(), receipt.qualityHoldLocationId(), line.lotNo()),
                    new InventoryDimension(line.productId(), line.targetWarehouseId(), targetLocationId, line.lotNo()),
                    disposition.quantity()));
        } else {
            InventoryCommandMetadata metadata = metadata(actor, disposition, line,
                    disposition.type() == QualityDispositionType.Scrap ? "QUALITY_SCRAP" : "QUALITY_RETURN",
                    receipt.receiptTime());
            mutation = inventoryCommandService.decrease(new InventoryDecreaseCommand(metadata,
                    new InventoryDimension(line.productId(), line.targetWarehouseId(), receipt.qualityHoldLocationId(), line.lotNo()),
                    disposition.quantity()));
        }
        if (mutation == null || mutation.transactions().isEmpty()) {
            throw new ServiceUnavailableException("质量处置库存变更结果为空");
        }
        if (disposition.type() == QualityDispositionType.Release) {
            UUID putawayTarget = request == null ? null : request.getPutawayTargetLocationId();
            UUID target = putawayTarget != null ? putawayTarget
                    : putawayTaskRepository.findDefaultStorageLocation(actor.tenantId(), line.targetWarehouseId())
                    .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004,
                            "目标仓库没有启用 Storage 库位"));
            requireStorage(actor.tenantId(), target, line.targetWarehouseId());
            putawayTaskRepository.insert(new PutawayTaskFact(UUID.randomUUID(), actor.tenantId(),
                    "PUT-" + UUID.randomUUID(), receipt.id(), line.id(), line.productId(), targetLocationId,
                    target, line.targetWarehouseId(), disposition.quantity(), "Pending", null, null,
                    actor.userId(), now(), null));
        }
        QualityDispositionFact completed = repository.completeDisposition(disposition, actor.userId(), now(),
                mutation.transactions().get(0).id());
        return dispositionView(completed);
    }

    private InventoryCommandMetadata metadata(Actor actor, QualityDispositionFact disposition,
                                              QualityReceiptLineFact line, String transactionType,
                                              OffsetDateTime businessTime) {
        return new InventoryCommandMetadata(actor.tenantId(), actor.userId(), actor.sessionId(), actor.requestId(),
                "quality-disposition-" + disposition.id(), digest("inventory-" + transactionType,
                List.of(disposition.id(), line.id(), disposition.quantity())), "PURCHASE_QUALITY_DISPOSITION",
                disposition.id(), line.id(), transactionType, businessTime);
    }

    private QualityInspectionView inspectionView(QualityInspectionFact inspection) {
        QualityReceiptFact receipt = requiredReceipt(inspection.tenantId(), inspection.purchaseReceiptId(), false);
        return QualityInspectionView.of(inspection, receipt.purchaseOrderNo(), receipt.purchaseOrderId());
    }

    private QualityDispositionView dispositionView(QualityDispositionFact disposition) {
        QualityInspectionFact inspection = requiredInspection(disposition.tenantId(), disposition.inspectionId(), false);
        return QualityDispositionView.of(disposition, inspectionView(inspection));
    }

    private QualityReceiptFact requiredReceipt(UUID tenantId, UUID id, boolean forUpdate) {
        return repository.findReceipt(tenantId, id, forUpdate)
                .orElseThrow(() -> new NotFoundException("采购收货单"));
    }

    private QualityInspectionFact requiredInspection(UUID tenantId, UUID id, boolean forUpdate) {
        return repository.findInspection(tenantId, id, forUpdate)
                .orElseThrow(() -> new NotFoundException("采购质检事实"));
    }

    private QualityReceiptLineFact requiredLine(QualityReceiptFact receipt, UUID lineId, UUID productId) {
        return receipt.lines().stream().filter(line -> line.id().equals(lineId) && line.productId().equals(productId))
                .findFirst().orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004,
                        "质检明细不属于当前收货单或产品不一致"));
    }

    private UUID requiredReceivingStaging(UUID tenantId, UUID locationId, UUID warehouseId) {
        PurchasingLocationFact location = referencePort.findActiveLocation(tenantId, locationId)
                .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004, "放行目标库位不存在或未启用"));
        if (!"ReceivingStaging".equals(location.type()) || !warehouseId.equals(location.warehouseId())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "放行目标必须是同仓 ReceivingStaging 库位");
        }
        return location.id();
    }

    private void requireStorage(UUID tenantId, UUID locationId, UUID warehouseId) {
        PurchasingLocationFact location = referencePort.findActiveLocation(tenantId, locationId)
                .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004, "上架目标库位不存在或未启用"));
        if (!"Storage".equals(location.type()) || !warehouseId.equals(location.warehouseId())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "上架目标必须是同仓 Storage 库位");
        }
    }

    private BigDecimal sum(List<QualityDispositionFact> facts, QualityDispositionType type) {
        return facts.stream().filter(fact -> fact.type() == type).map(QualityDispositionFact::quantity)
                .reduce(BigDecimal.ZERO.setScale(SCALE), BigDecimal::add);
    }

    private void validateInspectionRequest(UUID receiptId, QualityInspectionRequest request) {
        if (receiptId == null || request == null || request.getPurchaseReceiptId() == null
                || request.getPurchaseReceiptLineId() == null || request.getProductId() == null
                || !receiptId.equals(request.getPurchaseReceiptId())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "质检收货单和明细标识不能为空且必须一致");
        }
    }

    private BigDecimal decimal(String value, String field, boolean positive) {
        if (value == null || value.trim().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 不能为空");
        }
        try {
            BigDecimal result = new BigDecimal(value.trim());
            int integerDigits = Math.max(result.precision() - result.scale(), 0);
            if (result.scale() > SCALE || integerDigits > INTEGER_DIGITS
                    || (positive ? result.signum() <= 0 : result.signum() < 0)) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 数量不合法");
            }
            return result.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException exception) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 格式不正确");
        }
    }

    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = UserContextHolder.getSessionId();
        String requestId = RequestContextHolder.getRequestId();
        if (blank(sessionId) || blank(requestId)) {
            throw new ForbiddenException("缺失可信会话或请求上下文");
        }
        return new Actor(tenantId, userId, sessionId, requestId);
    }

    private void validateKey(String key) {
        if (blank(key) || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    private String digest(String operation, Object payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operation", operation);
        body.put("payload", payload);
        try {
            byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ServiceUnavailableException("质量幂等载荷摘要生成失败", exception);
        }
    }

    private String joinNote(String reason, String remark) {
        String left = normalize(reason);
        String right = normalize(remark);
        if (left == null) return right;
        if (right == null) return left;
        String joined = left + "；" + right;
        if (joined.length() > 512) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "质检说明不能超过 512 个字符");
        }
        return joined;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isBlank()) return null;
        if (value.trim().length() > 512) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "说明不能超过 512 个字符");
        }
        return value.trim();
    }

    private boolean blank(String value) { return value == null || value.trim().isBlank(); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) { }
}
