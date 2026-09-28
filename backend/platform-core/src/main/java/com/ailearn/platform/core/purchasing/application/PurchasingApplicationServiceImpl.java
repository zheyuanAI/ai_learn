package com.ailearn.platform.core.purchasing.application;

import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.operationaudit.application.OperationAuditCommand;
import com.ailearn.platform.core.operationaudit.application.OperationAuditRecorder;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.WorkOrderSourcePort;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrder;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPage;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPageQuery;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderRepository;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderStatus;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceipt;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceiptLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceiptStatus;
import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.PurchasingProductFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.core.purchasing.dto.PurchaseArrivalAcceptanceSummary;
import com.ailearn.platform.core.purchasing.dto.PurchaseBalanceDeltaSummary;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderCompleteRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderLineRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageResult;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderSaveRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderView;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptConfirmRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptLineRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseReceiptView;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * 采购订单与到货验收应用服务。
 * <p>
 * 订单状态、主数据引用和到货数量关系在本服务完成校验；收货库存变化只能经由
 * {@link InventoryCommandService#increase(InventoryIncreaseCommand)} 写入，并与采购事实处于同一事务。
 * </p>
 */
@Service
public class PurchasingApplicationServiceImpl implements PurchaseOrderApplicationService {

    private static final int SCALE = 6;
    private static final int INTEGER_DIGITS = 13;

    private final PurchaseOrderRepository repository;
    private final PurchasingReferencePort referencePort;
    private final WorkOrderSourcePort workOrderSourcePort;
    private final InventoryCommandService inventoryCommandService;
    private final PurchasingIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;
    private final OperationAuditRecorder operationAuditRecorder;

    /**
     * 提供纯单元测试使用的默认幂等构造器。
     */
    public PurchasingApplicationServiceImpl(PurchaseOrderRepository repository,
                                            PurchasingReferencePort referencePort,
                                            WorkOrderSourcePort workOrderSourcePort,
                                            InventoryCommandService inventoryCommandService) {
        this(repository, referencePort, workOrderSourcePort, inventoryCommandService,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建可替换幂等存储和序列化器的采购应用服务。
     *
     * @param repository 采购订单与收货持久化边界
     * @param referencePort 主数据只读端口
     * @param workOrderSourcePort 制造工单来源只读端口
     * @param inventoryCommandService 库存唯一写应用端口
     * @param storage Core 共享幂等存储
     * @param objectMapper 幂等载荷和结果序列化器
     */
    public PurchasingApplicationServiceImpl(PurchaseOrderRepository repository,
                                            PurchasingReferencePort referencePort,
                                            WorkOrderSourcePort workOrderSourcePort,
                                            InventoryCommandService inventoryCommandService,
                                            IdempotencyStorage storage,
                                            ObjectMapper objectMapper) {
        this(repository, referencePort, workOrderSourcePort, inventoryCommandService,
                storage, objectMapper, OperationAuditRecorder.NO_OP);
    }

    /**
     * 用途：为生产实例注入同事务采购操作记录；入参为既有业务端口、幂等存储和记录端口；出参为服务实例。
     * 流程：沿用原采购依赖，仅在首次成功业务动作中追加本地记录，重放不再写入。
     */
    @Autowired
    public PurchasingApplicationServiceImpl(PurchaseOrderRepository repository,
                                            PurchasingReferencePort referencePort,
                                            WorkOrderSourcePort workOrderSourcePort,
                                            InventoryCommandService inventoryCommandService,
                                            IdempotencyStorage storage,
                                            ObjectMapper objectMapper,
                                            OperationAuditRecorder operationAuditRecorder) {
        this.repository = repository;
        this.referencePort = referencePort;
        this.workOrderSourcePort = workOrderSourcePort;
        this.inventoryCommandService = inventoryCommandService;
        this.objectMapper = objectMapper;
        this.operationAuditRecorder = operationAuditRecorder;
        this.idempotencyExecutor = new PurchasingIdempotencyExecutor(storage, objectMapper);
    }

    /**
     * 查询当前租户订单，并由服务端计算允许动作。
     */
    @Override
    @PreAuthorize("hasAuthority('pur:order:view')")
    public PurchaseOrderPageResult page(com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageQuery query) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        PurchaseOrderPageQuery normalized = query == null
                ? new PurchaseOrderPageQuery(null, null, 1, 20) : query.normalized();
        PurchaseOrderPage page = repository.findPage(tenantId, normalized);
        return new PurchaseOrderPageResult(page.records().stream().map(this::toView).toList(),
                page.total(), page.page(), page.size());
    }

    /**
     * 查询当前租户订单详情，跨租户标识按不存在处理。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('pur:order:view')")
    public PurchaseOrderView detail(UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        // 修改用途：同一只读快照内读取订单和事实，避免并发收货时订单数量与履约累计来自不同提交点。
        PurchaseOrder order = findOrder(tenantId, id, false);
        // 修改用途：订单存在且属当前租户后才读取五类真实履约事实，避免跨租户或已删订单泄露累计。
        return new PurchaseOrderView(order, actions(order), repository.findLineCumulatives(tenantId, id));
    }

    /**
     * 创建 Draft 采购订单。
     * 入参：订单请求和 HTTP 幂等键；出参：Draft 订单；流程：可信上下文 -> 主数据/工单来源校验 -> 持久化。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:order:create')")
    public PurchaseOrderView create(PurchaseOrderSaveRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateSaveRequest(request, true);
        return idempotencyExecutor.execute("purchasing:order:create", actor.tenantId(), idempotencyKey,
                digest("create", request), PurchaseOrderView.class,
                () -> {
                    PurchaseOrderView view = toView(repository.insert(buildNewOrder(request, actor)));
                    // 修改：记录首次创建的账号与状态，和采购事实共同提交。
                    recordSuccess(actor, "pur:order:create", view.getId(), view.getPoNo(), null,
                            view.getStatus(), "创建采购订单", idempotencyKey);
                    return view;
                });
    }

    /**
     * 以客户端提供的订单版本修改 Draft 订单，防止旧页面覆盖新草稿。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:order:create')")
    public PurchaseOrderView update(UUID id, PurchaseOrderSaveRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateSaveRequest(request, false);
        if (request.getVersion() == null || request.getVersion() < 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "修改 Draft 采购单必须携带有效 version");
        }
        return idempotencyExecutor.execute("purchasing:order:update", actor.tenantId(), idempotencyKey,
                digest("update", List.of(id, request)), PurchaseOrderView.class,
                () -> {
                    PurchaseOrderView view = updateDraft(id, request, actor);
                    // 修改：仅记录成功修改，幂等重放直接返回原结果。
                    recordSuccess(actor, "pur:order:update", view.getId(), view.getPoNo(), "Draft",
                            view.getStatus(), "修改采购订单草稿", idempotencyKey);
                    return view;
                });
    }

    /**
     * 将 Draft 提交为 Submitted。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:order:submit')")
    public PurchaseOrderView submit(UUID id, String idempotencyKey) {
        return transition("purchasing:order:submit", id, idempotencyKey,
                (order, actor, now) -> order.submit(actor.userId(), now));
    }

    /**
     * 将 Submitted 审核为 Approved；审核不自动收货或增加库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:order:approve')")
    public PurchaseOrderView approve(UUID id, String idempotencyKey) {
        return transition("purchasing:order:approve", id, idempotencyKey,
                (order, actor, now) -> order.approve(actor.userId(), now));
    }

    /**
     * 人工完成采购订单，终止剩余待收数量但不伪造收货、上架或库存流水。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:order:complete')")
    public PurchaseOrderView manuallyComplete(UUID id, PurchaseOrderCompleteRequest request,
                                               String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (request == null || request.getCompletionReason() == null
                || request.getCompletionReason().trim().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "人工完成原因不能为空");
        }
        String reason = request.getCompletionReason().trim();
        if (reason.length() > 512) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "人工完成原因不能超过 512 个字符");
        }
        return idempotencyExecutor.execute("purchasing:order:complete", actor.tenantId(), idempotencyKey,
                digest("manual-complete", List.of(id, reason)), PurchaseOrderView.class,
                () -> {
                    PurchaseOrder order = findOrder(actor.tenantId(), id, true);
                    PurchaseOrder completed = order.manuallyComplete(reason, actor.userId(), actor.sessionId(), now());
                    PurchaseOrderView view = toView(repository.updateState(completed, order.version()));
                    // 修改：完成原因仍由业务表保存，操作记录只保留动作摘要。
                    recordSuccess(actor, "pur:order:complete", view.getId(), view.getPoNo(),
                            order.status().name(), view.getStatus(), "人工完成采购订单", idempotencyKey);
                    return view;
                });
    }

    /**
     * 确认到货外观验收。
     * 入参：收货事实、订单和质量隔离库位；出参：验收汇总及库存流水；流程：锁订单 -> 校验完整批次 ->
     * 写收货事实 -> 对每个实际接收明细调用一次 increase -> 更新采购累计收货，异常整体回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:receipt:confirm')")
    public PurchaseReceiptView confirmReceipt(PurchaseReceiptConfirmRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        // 修改：由服务端根据可信租户与幂等键分配收货事实 ID，避免客户端伪造或复用订单 ID。
        UUID receiptId = UUID.nameUUIDFromBytes(("purchase-receipt|" + actor.tenantId() + "|"
                + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        return confirmReceipt(receiptId, request, idempotencyKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('pur:receipt:confirm')")
    public PurchaseReceiptView confirmReceipt(UUID receiptId, PurchaseReceiptConfirmRequest request,
                                              String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateReceiptRequest(receiptId, request);
        return idempotencyExecutor.execute("purchasing:receipt:confirm", actor.tenantId(), idempotencyKey,
                digest("receipt-confirm", List.of(receiptId, request)), PurchaseReceiptView.class,
                () -> {
                    PurchaseReceiptView view = confirmReceiptInternal(receiptId, request, actor);
                    // 修改：在所属采购订单时间线记录收货，不额外保存原始表单。
                    PurchaseOrder order = findOrder(actor.tenantId(), request.getPurchaseOrderId(), false);
                    recordSuccess(actor, "pur:receipt:confirm", order.id(), order.poNo(), null,
                            order.status().name(), "确认采购到货验收 " + view.getReceiptNo(), idempotencyKey);
                    return view;
                });
    }

    private PurchaseOrderView updateDraft(UUID id, PurchaseOrderSaveRequest request, Actor actor) {
        PurchaseOrder current = findOrder(actor.tenantId(), id, true);
        validateSupplier(actor.tenantId(), request.getSupplierId());
        List<PurchaseOrderLine> lines = buildLines(request.getLines(), actor.tenantId(), current.lines());
        PurchaseOrder updated = current.draftUpdated(request.getSupplierId(), request.getExpectedArrivalDate(),
                normalizeRemark(request.getRemark()), lines, actor.userId(), now());
        return toView(repository.updateDraft(updated, request.getVersion()));
    }

    private PurchaseOrderView transition(String operation, UUID id, String key, Transition transition) {
        Actor actor = actor();
        validateKey(key);
        return idempotencyExecutor.execute(operation, actor.tenantId(), key, digest(operation, id),
                PurchaseOrderView.class, () -> {
                    PurchaseOrder order = findOrder(actor.tenantId(), id, true);
                    PurchaseOrder next = transition.apply(order, actor, now());
                    PurchaseOrderView view = toView(repository.updateState(next, order.version()));
                    // 修改：状态迁移成功后记录真实操作者，记录失败随业务事务回滚。
                    String action = operation.equals("purchasing:order:submit")
                            ? "pur:order:submit" : "pur:order:approve";
                    recordSuccess(actor, action, view.getId(), view.getPoNo(), order.status().name(),
                            view.getStatus(), action.endsWith("submit") ? "提交采购订单" : "审核采购订单", key);
                    return view;
                });
    }

    private PurchaseOrder buildNewOrder(PurchaseOrderSaveRequest request, Actor actor) {
        validateSupplier(actor.tenantId(), request.getSupplierId());
        List<PurchaseOrderLine> lines = buildLines(request.getLines(), actor.tenantId(), List.of());
        OffsetDateTime at = now();
        String poNo = request.getPoNo() == null || request.getPoNo().trim().isBlank()
                ? "PO-" + UUID.randomUUID() : request.getPoNo().trim();
        return new PurchaseOrder(UUID.randomUUID(), actor.tenantId(), poNo, request.getSupplierId(),
                request.getExpectedArrivalDate(), PurchaseOrderStatus.Draft, null, null, null, null, null,
                normalizeRemark(request.getRemark()), 0L, actor.userId(), at, actor.userId(), at, lines);
    }

    private List<PurchaseOrderLine> buildLines(List<PurchaseOrderLineRequest> requests, UUID tenantId,
                                               List<PurchaseOrderLine> oldLines) {
        Map<Integer, UUID> oldIds = new HashMap<>();
        oldLines.forEach(line -> oldIds.put(line.lineNo(), line.id()));
        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            PurchaseOrderLineRequest request = requests.get(index);
            if (request == null || request.getProductId() == null || request.getUom() == null
                    || request.getUom().trim().isBlank() || request.getTargetWarehouseId() == null) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "采购订单明细必要字段不能为空");
            }
            PurchasingProductFact product = referencePort.findActiveProduct(tenantId, request.getProductId())
                    .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004,
                            "产品不存在、停用或不属于当前租户"));
            String uom = request.getUom().trim();
            if (product.uom() == null || !product.uom().equals(uom)) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "订单行计量单位与产品主数据不一致");
            }
            if (!referencePort.isActiveWarehouse(tenantId, request.getTargetWarehouseId())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "目标仓库不存在、停用或不属于当前租户");
            }
            validateSourceWorkOrder(tenantId, request.getSourceWorkOrderId(), request.getProductId());
            int lineNo = request.getLineNo() == null ? index + 1 : request.getLineNo();
            if (lineNo <= 0) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "lineNo 必须大于 0");
            }
            // 未提供行号时按请求顺序生成，避免多明细全部落到同一默认行号。
            lines.add(new PurchaseOrderLine(oldIds.getOrDefault(lineNo, UUID.randomUUID()), tenantId, lineNo,
                    request.getProductId(), uom, parsePositive(request.getOrderedQty(), "orderedQty"),
                    BigDecimal.ZERO.setScale(SCALE), request.getTargetWarehouseId(), request.getSourceWorkOrderId()));
        }
        return List.copyOf(lines);
    }

    private PurchaseReceiptView confirmReceiptInternal(UUID receiptId, PurchaseReceiptConfirmRequest request,
                                                        Actor actor) {
        PurchaseOrder order = findOrder(actor.tenantId(), request.getPurchaseOrderId(), true);
        if (!order.receivingAllowed()) {
            throw new PurchasingException(PurchasingErrorCode.PO_001,
                    "只有 Approved 或 PartiallyReceived 采购单允许收货");
        }
        PurchasingLocationFact holdLocation = referencePort.findActiveLocation(actor.tenantId(),
                        request.getQualityHoldLocationId())
                .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004,
                        "质量隔离库位不存在、停用或不属于当前租户"));
        if (!"QualityHold".equals(holdLocation.type())) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "收货目标库位必须是 QualityHold");
        }
        List<PurchaseReceiptLine> receiptLines = new ArrayList<>();
        Map<UUID, BigDecimal> receivedDeltas = new LinkedHashMap<>();
        Set<UUID> seenOrderLines = new HashSet<>();
        for (PurchaseReceiptLineRequest requestLine : request.getLines()) {
            PurchaseOrderLine orderLine = resolveOrderLine(order, requestLine);
            if (!seenOrderLines.add(orderLine.id())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "同一收货单不能重复提交采购明细");
            }
            if (!orderLine.targetWarehouseId().equals(holdLocation.warehouseId())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004,
                        "质量隔离库位必须属于采购明细目标仓库");
            }
            if (!referencePort.isActiveWarehouse(actor.tenantId(), orderLine.targetWarehouseId())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004,
                        "采购明细目标仓库不存在、停用或不属于当前租户");
            }
            PurchasingProductFact product = referencePort.findActiveProduct(actor.tenantId(), orderLine.productId())
                    .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004, "收货产品不可用"));
            String uom = requiredText(requestLine.getUom(), "uom", 64);
            if (!orderLine.uom().equals(uom) || !orderLine.productId().equals(requestLine.getProductId())
                    || product.uom() == null || !product.uom().equals(uom)) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "收货产品或计量单位与采购明细不一致");
            }
            String lotNo = normalizeLot(requestLine.getLotNo());
            if (product.batchManaged() && lotNo.isBlank()) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "批次管理产品收货时必须填写 lotNo");
            }
            if (!product.batchManaged() && !lotNo.isBlank()) {
                throw new PurchasingException(PurchasingErrorCode.PO_004, "非批次管理产品不能填写 lotNo");
            }
            BigDecimal arrived = parsePositive(requestLine.getArrivedQty(), "arrivedQty");
            BigDecimal rejected = parseNonNegative(requestLine.getRejectedQty(), "rejectedQty");
            BigDecimal received = parseNonNegative(requestLine.getReceivedQty(), "receivedQty");
            if (arrived.compareTo(orderLine.pendingQty()) > 0) {
                throw new PurchasingException(PurchasingErrorCode.PO_002, "到货数量超过采购明细当前待收数量");
            }
            PurchaseReceiptLine receiptLine = new PurchaseReceiptLine(UUID.randomUUID(), actor.tenantId(),
                    orderLine.id(), orderLine.lineNo(), orderLine.productId(), uom, arrived, rejected, received,
                    lotNo, requestLine.getRejectionReason());
            receiptLines.add(receiptLine);
            receivedDeltas.put(orderLine.id(), received);
        }
        OffsetDateTime at = now();
        String receiptNo = request.getReceiptNo() == null || request.getReceiptNo().trim().isBlank()
                ? "PR-" + UUID.randomUUID() : request.getReceiptNo().trim();
        PurchaseReceipt receipt = new PurchaseReceipt(receiptId, actor.tenantId(), receiptNo,
                order.id(), request.getReceiptTime(), request.getQualityHoldLocationId(), PurchaseReceiptStatus.Confirmed,
                actor.userId(), actor.sessionId(), at, 0L, actor.userId(), at, actor.userId(), at, receiptLines);
        PurchaseReceipt savedReceipt = repository.insertReceipt(receipt);

        // 修改：收齐整次实收维度后一次预锁，避免不同采购单以反向明细顺序逐行占锁。
        List<InventoryDimension> dimensions = receiptLines.stream()
                .filter(line -> line.receivedQty().signum() > 0)
                .map(line -> new InventoryDimension(line.productId(), holdLocation.warehouseId(),
                        savedReceipt.qualityHoldLocationId(), line.lotNo()))
                .toList();
        if (!dimensions.isEmpty()) {
            inventoryCommandService.lockBalances(dimensions);
        }
        List<InventoryTransaction> transactions = new ArrayList<>();
        for (PurchaseReceiptLine line : receiptLines) {
            if (line.receivedQty().signum() == 0) {
                continue;
            }
            PurchaseOrderLine orderLine = order.lines().stream()
                    .filter(candidate -> candidate.id().equals(line.purchaseOrderLineId()))
                    .findFirst()
                    .orElseThrow(() -> new ServiceUnavailableException("采购订单行读取结果不完整"));
            InventoryCommandMetadata metadata = new InventoryCommandMetadata(actor.tenantId(), actor.userId(),
                    actor.sessionId(), actor.requestId(), "purchase-receipt-" + savedReceipt.id() + "-" + line.id(),
                    digest("inventory-receipt", List.of(savedReceipt.id(), line.id(), line.receivedQty())),
                    "PURCHASE_RECEIPT", savedReceipt.id(), line.id(), "RECEIPT", savedReceipt.receiptTime());
            InventoryMutationResult mutation = inventoryCommandService.increase(new InventoryIncreaseCommand(metadata,
                    new InventoryDimension(line.productId(), orderLine.targetWarehouseId(),
                            savedReceipt.qualityHoldLocationId(), line.lotNo()), line.receivedQty()));
            if (mutation == null) {
                throw new ServiceUnavailableException("库存收货增加结果为空");
            }
            transactions.addAll(mutation.transactions());
        }
        if (receivedDeltas.values().stream().anyMatch(quantity -> quantity.signum() > 0)) {
            PurchaseOrder updated = order.applyReceipt(receivedDeltas, actor.userId(), actor.sessionId(), at);
            // Repository 在 PostgreSQL 中以订单版本条件更新；订单已被 FOR UPDATE 锁定，避免并发收货覆盖。
            repository.updateState(updated, order.version());
        }
        return toReceiptView(savedReceipt, transactions);
    }

    private PurchaseOrderLine resolveOrderLine(PurchaseOrder order, PurchaseReceiptLineRequest request) {
        if (request == null) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "收货明细不能为空");
        }
        if (request.getPurchaseOrderLineId() == null) {
            throw new PurchasingException(PurchasingErrorCode.PO_004,
                    "收货明细必须携带 purchaseOrderLineId");
        }
        return order.lines().stream()
                .filter(line -> line.id().equals(request.getPurchaseOrderLineId()))
                .findFirst()
                .orElseThrow(() -> new PurchasingException(PurchasingErrorCode.PO_004,
                        "收货明细不属于当前采购订单"));
    }

    private void validateSaveRequest(PurchaseOrderSaveRequest request, boolean creating) {
        if (request == null || request.getSupplierId() == null || request.getExpectedArrivalDate() == null
                || request.getLines() == null || request.getLines().isEmpty()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "供应商、计划到货日期和明细不能为空");
        }
        if (creating && request.getVersion() != null) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "创建采购单不能指定 version");
        }
        if (request.getPoNo() != null && !request.getPoNo().trim().isBlank()
                && request.getPoNo().trim().length() > 64) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "采购订单号不能超过 64 个字符");
        }
        normalizeRemark(request.getRemark());
    }

    private void validateReceiptRequest(UUID receiptId, PurchaseReceiptConfirmRequest request) {
        if (receiptId == null || request == null || request.getPurchaseOrderId() == null
                || request.getReceiptTime() == null || request.getQualityHoldLocationId() == null
                || request.getLines() == null || request.getLines().isEmpty()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004,
                    "收货单、采购订单、收货时间、质量隔离库位和明细不能为空");
        }
        if (request.getReceiptNo() != null && !request.getReceiptNo().trim().isBlank()
                && request.getReceiptNo().trim().length() > 64) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "收货单号不能超过 64 个字符");
        }
    }

    private void validateSupplier(UUID tenantId, UUID supplierId) {
        if (!referencePort.isActiveSupplier(tenantId, supplierId)) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "供应商不存在、停用或不属于当前租户");
        }
    }

    private void validateSourceWorkOrder(UUID tenantId, UUID workOrderId, UUID productId) {
        if (workOrderId != null
                && workOrderSourcePort.findActiveForProcurement(tenantId, workOrderId, productId).isEmpty()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004,
                    "来源生产工单不存在、已删除、跨租户，或采购物料不属于工单产出品/BOM 组件");
        }
    }

    private PurchaseOrder findOrder(UUID tenantId, UUID id, boolean forUpdate) {
        if (id == null) {
            throw new NotFoundException("采购订单不存在");
        }
        return (forUpdate ? repository.findByIdForUpdate(tenantId, id) : repository.findById(tenantId, id))
                .orElseThrow(() -> new NotFoundException("采购订单不存在"));
    }

    private PurchaseOrderView toView(PurchaseOrder order) {
        if (order == null) {
            throw new ServiceUnavailableException("采购订单持久化结果为空");
        }
        return new PurchaseOrderView(order, actions(order));
    }

    private List<AllowedActionVo> actions(PurchaseOrder order) {
        return switch (order.status()) {
            case Draft -> List.of(action("update", true), action("submit", true));
            case Submitted -> List.of(action("approve", true));
            case Approved, PartiallyReceived -> List.of(action("confirmReceipt", true), action("manualComplete", true));
            case Completed -> List.of();
        };
    }

    private List<AllowedActionVo> receiptActions(PurchaseReceipt receipt) {
        return receipt.status() == PurchaseReceiptStatus.Confirmed ? List.of() : List.of(action("confirm", true));
    }

    private AllowedActionVo action(String name, boolean enabled) {
        return new AllowedActionVo(name, enabled, enabled ? null : "当前采购单状态不允许此操作");
    }

    private PurchaseReceiptView toReceiptView(PurchaseReceipt receipt, List<InventoryTransaction> transactions) {
        BigDecimal arrived = receipt.lines().stream().map(PurchaseReceiptLine::arrivedQty)
                .reduce(BigDecimal.ZERO.setScale(SCALE), BigDecimal::add);
        BigDecimal rejected = receipt.lines().stream().map(PurchaseReceiptLine::rejectedQty)
                .reduce(BigDecimal.ZERO.setScale(SCALE), BigDecimal::add);
        BigDecimal received = receipt.lines().stream().map(PurchaseReceiptLine::receivedQty)
                .reduce(BigDecimal.ZERO.setScale(SCALE), BigDecimal::add);
        return new PurchaseReceiptView(receipt,
                new PurchaseArrivalAcceptanceSummary(text(arrived), text(rejected), text(received)),
                new PurchaseBalanceDeltaSummary(text(received), received.signum() > 0,
                        receipt.qualityHoldLocationId()), transactions, receiptActions(receipt));
    }

    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = UserContextHolder.getSessionId();
        String requestId = RequestContextHolder.getRequestId();
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            throw new ForbiddenException("缺失可信会话或请求上下文");
        }
        return new Actor(tenantId, userId, sessionId, requestId);
    }

    /**
     * 用途：追加采购订单成功操作记录；入参为可信账号上下文、订单身份、状态与幂等键；出参为无。
     * 流程：复用本地记录端口，账号从服务端上下文取得，仅保存固定业务摘要。
     */
    private void recordSuccess(Actor actor, String actionCode, UUID orderId, String orderNo,
                               String beforeStatus, String afterStatus, String summary, String key) {
        operationAuditRecorder.recordSuccess(new OperationAuditCommand(actor.tenantId(), "USER",
                actor.userId(), UserContextHolder.getUsername(), actor.sessionId(), actor.requestId(),
                actionCode, "PURCHASE_ORDER", orderId, orderNo, beforeStatus, afterStatus,
                summary, "status,lines", key, now()));
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    private String normalizeRemark(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 512) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "remark 不能超过 512 个字符");
        }
        return normalized;
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.trim().isBlank() || value.trim().length() > maxLength) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 不能为空且不能超过 " + maxLength + " 个字符");
        }
        return value.trim();
    }

    private String normalizeLot(String lotNo) {
        String value = lotNo == null || lotNo.isBlank() ? "" : lotNo.trim();
        if (value.length() > 128) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, "lotNo 不能超过 128 个字符");
        }
        return value;
    }

    private BigDecimal parsePositive(String value, String field) {
        BigDecimal parsed = parseDecimal(value, field);
        if (parsed.signum() <= 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 必须大于 0");
        }
        return parsed;
    }

    private BigDecimal parseNonNegative(String value, String field) {
        BigDecimal parsed = parseDecimal(value, field);
        if (parsed.signum() < 0) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 不能为负数");
        }
        return parsed;
    }

    private BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.trim().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 不能为空");
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            if (parsed.scale() > SCALE || Math.max(parsed.precision() - parsed.scale(), 0) > INTEGER_DIGITS) {
                throw new PurchasingException(PurchasingErrorCode.PO_004,
                        field + " 超出 NUMERIC(19,6) 范围");
            }
            return parsed.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException exception) {
            throw new PurchasingException(PurchasingErrorCode.PO_004, field + " 格式不正确");
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
            throw new ServiceUnavailableException("采购幂等载荷摘要生成失败", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String text(BigDecimal value) {
        return value.setScale(SCALE).toPlainString();
    }

    @FunctionalInterface
    private interface Transition {
        PurchaseOrder apply(PurchaseOrder order, Actor actor, OffsetDateTime now);
    }

    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }
}
