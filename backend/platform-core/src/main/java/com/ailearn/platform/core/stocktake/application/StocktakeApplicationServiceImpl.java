package com.ailearn.platform.core.stocktake.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryDecreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryIncreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.stocktake.domain.StocktakeLine;
import com.ailearn.platform.core.stocktake.domain.StocktakeOrder;
import com.ailearn.platform.core.stocktake.domain.StocktakeRepository;
import com.ailearn.platform.core.stocktake.domain.StocktakeStatus;
import com.ailearn.platform.core.stocktake.dto.StocktakeConfirmRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCountLineRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeCreateRequest;
import com.ailearn.platform.core.stocktake.dto.StocktakeView;
import com.ailearn.platform.core.stocktake.exception.StocktakeErrorCode;
import com.ailearn.platform.core.stocktake.exception.StocktakeException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.ForbiddenException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 盘点应用服务实现。
 * <p>
 * 开始盘点只冻结系统快照；确认时重新校验每条余额版本，再通过 InventoryCommandService 生成差异调整，
 * 不直接更新库存表。整个确认过程和盘点状态推进共享一个事务。
 * </p>
 */
@Service
public class StocktakeApplicationServiceImpl implements StocktakeApplicationService {

    private static final int SNAPSHOT_PAGE_SIZE = 200;

    private final StocktakeRepository repository;
    private final com.ailearn.platform.core.inventory.application.InventoryQueryService inventoryQueryService;
    private final InventoryCommandService inventoryCommandService;
    private final CoreIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;
    private final WarehouseReferencePort warehouseReferencePort;
    private final InventoryLocationPort inventoryLocationPort;

    /**
     * 提供纯单元测试使用的内存幂等构造器。
     *
     * @param repository 盘点持久化端口
     * @param inventoryQueryService 库存查询端口
     * @param inventoryCommandService 库存写端口
     */
    public StocktakeApplicationServiceImpl(StocktakeRepository repository,
                                           com.ailearn.platform.core.inventory.application.InventoryQueryService inventoryQueryService,
                                           InventoryCommandService inventoryCommandService) {
        this(repository, inventoryQueryService, inventoryCommandService,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()), null, null);
    }

    /**
     * 创建可替换幂等存储的盘点服务。
     *
     * @param repository 盘点持久化端口
     * @param inventoryQueryService 库存查询端口
     * @param inventoryCommandService 库存写端口
     * @param storage Core 幂等存储
     * @param objectMapper 结果序列化器
     */
    @Autowired
    public StocktakeApplicationServiceImpl(StocktakeRepository repository,
                                           com.ailearn.platform.core.inventory.application.InventoryQueryService inventoryQueryService,
                                           InventoryCommandService inventoryCommandService,
                                           IdempotencyStorage storage,
                                           ObjectMapper objectMapper,
                                           WarehouseReferencePort warehouseReferencePort,
                                           InventoryLocationPort inventoryLocationPort) {
        this.repository = repository;
        this.inventoryQueryService = inventoryQueryService;
        this.inventoryCommandService = inventoryCommandService;
        this.objectMapper = objectMapper;
        this.idempotencyExecutor = new CoreIdempotencyExecutor(storage, objectMapper);
        this.warehouseReferencePort = warehouseReferencePort;
        this.inventoryLocationPort = inventoryLocationPort;
    }

    /**
     * 创建未盘点盘点单。
     * 入参：仓库和可选库位范围、HTTP 幂等键；出参：未盘点视图；流程：可信上下文校验 -> 生成租户内聚合
     * -> 持久化，重复请求按操作域重放首次结果。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('inv:stocktake:create')")
    public StocktakeView create(StocktakeCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateCreateRequest(request);
        validateMasterDataReferences(request, actor.tenantId());
        return idempotencyExecutor.execute("stocktake:create", actor.tenantId(), idempotencyKey,
                digest("create", request), StocktakeView.class,
                () -> createInternal(request, actor));
    }

    /**
     * 开始盘点并保存系统余额快照。
     * 入参：盘点单 ID 和幂等键；出参：Counting 视图及冻结明细；流程：读取未盘点单 -> 分页读取库存事实 ->
     * 版本条件推进状态并批量保存快照明细。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('inv:stocktake:start')")
    public StocktakeView start(UUID id, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (id == null) {
            throw new ValidationException("盘点单 ID 不能为空");
        }
        return idempotencyExecutor.execute("stocktake:start", actor.tenantId(), idempotencyKey,
                digest("start", id), StocktakeView.class,
                () -> startInternal(id, actor));
    }

    /**
     * 确认实盘并执行库存调整。
     * 入参：盘点单 ID、每条快照明细的实盘数量/原因和幂等键；出参：ConfirmedAdjusted 视图及调整流水；
     * 流程：校验完整明细和余额版本 -> 校验预留不变量 -> 对差异调用库存增加/减少 -> 版本条件确认。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('inv:stocktake:confirm')")
    public StocktakeView confirm(UUID id, StocktakeConfirmRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (id == null) {
            throw new ValidationException("盘点单 ID 不能为空");
        }
        List<Object> confirmPayload = new ArrayList<>();
        confirmPayload.add(id);
        confirmPayload.add(request);
        return idempotencyExecutor.execute("stocktake:confirm", actor.tenantId(), idempotencyKey,
                digest("confirm", confirmPayload), StocktakeView.class,
                () -> confirmInternal(id, request, actor));
    }

    /**
     * 创建盘点聚合。
     */
    private StocktakeView createInternal(StocktakeCreateRequest request, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        String stocktakeNo = request.getStocktakeNo() == null || request.getStocktakeNo().isBlank()
                ? "ST-" + id : request.getStocktakeNo().trim();
        StocktakeOrder order = new StocktakeOrder(id, actor.tenantId(), stocktakeNo,
                request.getWarehouseId(), request.getLocationId(), StocktakeStatus.NotStarted, 0L,
                null, null, null, null, actor.userId(), now, actor.userId(), now, List.of());
        return new StocktakeView(repository.insert(order), List.of(), actions(StocktakeStatus.NotStarted));
    }

    /**
     * 读取库存余额并构造系统快照明细。
     */
    private StocktakeView startInternal(UUID id, Actor actor) {
        StocktakeOrder order = findOrder(actor.tenantId(), id);
        if (order.status() != StocktakeStatus.NotStarted) {
            throw new StocktakeException(StocktakeErrorCode.ST_001, "只有未盘点单允许开始盘点");
        }
        List<InventoryBalance> balances = new ArrayList<>();
        int page = 1;
        while (true) {
            InventoryBalancePage balancePage = inventoryQueryService.queryBalances(new InventoryBalanceQuery(
                    actor.tenantId(), null, order.warehouseId(), order.locationId(), null,
                    page, SNAPSHOT_PAGE_SIZE));
            if (balancePage == null) {
                throw new ServiceUnavailableException("库存快照查询未返回结果");
            }
            balances.addAll(balancePage.content());
            if (!balancePage.hasNext()) {
                break;
            }
            page++;
        }
        List<StocktakeLine> snapshotLines = snapshotLines(order, actor.tenantId(), balances);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!repository.start(actor.tenantId(), id, order.version(), actor.userId(), now)) {
            throw new StocktakeException(StocktakeErrorCode.ST_001, "盘点单版本或状态已变化");
        }
        if (repository.insertLines(id, snapshotLines, actor.userId()) != snapshotLines.size()) {
            throw new ServiceUnavailableException("盘点系统快照明细数量不一致");
        }
        StocktakeOrder started = order.started(actor.userId(), now, snapshotLines);
        return new StocktakeView(started, List.of(), actions(StocktakeStatus.Counting));
    }

    /**
     * 校验实盘请求、余额版本并逐条执行受控调整。
     */
    private StocktakeView confirmInternal(UUID id, StocktakeConfirmRequest request, Actor actor) {
        StocktakeOrder order = findOrder(actor.tenantId(), id);
        if (order.status() != StocktakeStatus.Counting) {
            throw new StocktakeException(StocktakeErrorCode.ST_001, "只有盘点中单据允许确认");
        }
        Map<UUID, StocktakeCountLineRequest> requestByLine = validateCountRequest(request, order.lines());
        List<StocktakeLine> confirmedLines = new ArrayList<>();
        List<UUID> transactionIds = new ArrayList<>();
        for (StocktakeLine line : order.lines()) {
            StocktakeCountLineRequest countRequest = requestByLine.get(line.id());
            BigDecimal countedQty = parseCountedQty(countRequest.getCountedQty());
            String reason = normalizeReason(countRequest.getVarianceReason());
            InventoryBalance current = currentBalance(actor.tenantId(), line);
            if (current == null || current.version() != line.systemBalanceVersion()
                    || current.onHandQty().compareTo(line.systemQty()) != 0) {
                throw new com.ailearn.platform.core.inventory.exception.InventoryException(
                        com.ailearn.platform.core.inventory.exception.InventoryErrorCode.INV_003,
                        "盘点系统快照版本已变化，请重新开始盘点");
            }
            if (countedQty.compareTo(current.reservedQty()) < 0) {
                throw new StocktakeException(StocktakeErrorCode.ST_002,
                        "实盘数量不能低于当前有效预留数量");
            }
            BigDecimal variance = countedQty.subtract(line.systemQty())
                    .setScale(InventoryInvariant.SCALE);
            if (variance.signum() != 0 && (reason == null || reason.isBlank())) {
                throw new StocktakeException(StocktakeErrorCode.ST_002, "盘点差异必须填写原因");
            }
            StocktakeLine confirmedLine = line.counted(countedQty, reason);
            if (variance.signum() != 0) {
                UUID transactionId = adjust(actor, order, confirmedLine, variance);
                confirmedLine = confirmedLine.withAdjustment(transactionId);
                transactionIds.add(transactionId);
            }
            confirmedLines.add(confirmedLine);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!repository.confirm(actor.tenantId(), id, order.version(), confirmedLines, actor.userId(), now)) {
            throw new StocktakeException(StocktakeErrorCode.ST_001, "盘点单版本或状态已变化");
        }
        return new StocktakeView(order.confirmed(actor.userId(), now, confirmedLines),
                transactionIds, actions(StocktakeStatus.ConfirmedAdjusted));
    }

    /**
     * 把盘点差异转换为库存增加或减少命令；非零差异必须得到一条调整流水。
     */
    private UUID adjust(Actor actor, StocktakeOrder order, StocktakeLine line, BigDecimal variance) {
        String key = "stocktake:" + order.id() + ":line:" + line.id();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String digest = digest("adjustment", List.of(order.id(), line.id(), line.countedQty(), variance));
        InventoryCommandMetadata metadata = new InventoryCommandMetadata(actor.tenantId(), actor.userId(),
                actor.sessionId(), actor.requestId(), key, digest, "STOCKTAKE", order.id(), line.id(),
                "ADJUSTMENT", now);
        InventoryMutationResult result;
        if (variance.signum() > 0) {
            result = inventoryCommandService.increase(new InventoryIncreaseCommand(metadata, line.dimension(), variance,
                    line.systemBalanceVersion()));
        } else {
            result = inventoryCommandService.decrease(
                    new InventoryDecreaseCommand(metadata, line.dimension(), variance.abs(),
                            line.systemBalanceVersion()));
        }
        if (result == null || result.transactions() == null || result.transactions().isEmpty()) {
            throw new StocktakeException(StocktakeErrorCode.ST_002, "盘点差异调整流水生成失败");
        }
        InventoryTransaction transaction = result.transactions().getFirst();
        if (transaction == null || transaction.id() == null) {
            throw new StocktakeException(StocktakeErrorCode.ST_002, "盘点差异调整流水标识缺失");
        }
        return transaction.id();
    }

    /**
     * 从库存分页结果生成确定行号的快照，并拒绝重复库存维度。
     */
    private List<StocktakeLine> snapshotLines(StocktakeOrder order, UUID tenantId,
                                              List<InventoryBalance> balances) {
        Set<com.ailearn.platform.core.inventory.domain.InventoryDimension> dimensions = new HashSet<>();
        List<StocktakeLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (InventoryBalance balance : balances) {
            if (balance == null || !tenantId.equals(balance.tenantId()) || balance.dimension() == null) {
                throw new ServiceUnavailableException("库存快照包含非法租户或维度");
            }
            if (!order.warehouseId().equals(balance.dimension().warehouseId())
                    || (order.locationId() != null && !order.locationId().equals(balance.dimension().locationId()))) {
                throw new ServiceUnavailableException("库存快照超出盘点范围");
            }
            if (!dimensions.add(balance.dimension())) {
                throw new ServiceUnavailableException("库存快照包含重复维度");
            }
            lines.add(new StocktakeLine(UUID.randomUUID(), tenantId, lineNo++, balance.dimension().productId(),
                    balance.dimension().warehouseId(), balance.dimension().locationId(),
                    balance.dimension().normalizedLotNo(), balance.onHandQty(), balance.version(),
                    null, null, null));
        }
        return List.copyOf(lines);
    }

    /**
     * 按精确库存维度查询确认时的最新余额。
     */
    private InventoryBalance currentBalance(UUID tenantId, StocktakeLine line) {
        InventoryBalancePage page = inventoryQueryService.queryBalances(new InventoryBalanceQuery(
                tenantId, line.productId(), line.warehouseId(), line.locationId(), line.lotNo(), 1, 2));
        if (page == null || page.content() == null) {
            throw new ServiceUnavailableException("库存当前余额查询未返回结果");
        }
        return page.content().stream()
                .filter(balance -> balance != null && line.dimension().equals(balance.dimension()))
                .findFirst().orElse(null);
    }

    /**
     * 校验确认请求必须逐条覆盖系统快照且不允许重复行。
     */
    private Map<UUID, StocktakeCountLineRequest> validateCountRequest(
            StocktakeConfirmRequest request, List<StocktakeLine> snapshotLines) {
        if (request == null || request.getLines() == null
                || request.getLines().size() != snapshotLines.size()) {
            throw new ValidationException("盘点确认必须完整提交所有快照明细");
        }
        Map<UUID, StocktakeCountLineRequest> byLine = new HashMap<>();
        Set<UUID> expected = snapshotLines.stream().map(StocktakeLine::id).collect(java.util.stream.Collectors.toSet());
        for (StocktakeCountLineRequest line : request.getLines()) {
            if (line == null || line.getLineId() == null || !expected.contains(line.getLineId())
                    || byLine.put(line.getLineId(), line) != null) {
                throw new ValidationException("盘点确认明细必须来自当前盘点快照且不能重复");
            }
        }
        return byLine;
    }

    /**
     * 解析非负实盘数量字符串。
     */
    private BigDecimal parseCountedQty(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("实盘数量不能为空");
        }
        try {
            return InventoryInvariant.requireNonNegative("countedQty", new BigDecimal(value.trim()));
        } catch (NumberFormatException exception) {
            throw new ValidationException("实盘数量格式不正确");
        }
    }

    /**
     * 规范化差异原因文本。
     */
    private String normalizeReason(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 512) {
            throw new ValidationException("盘点差异原因不能超过 512 个字符");
        }
        return normalized;
    }

    /**
     * 读取当前租户的盘点聚合，隐藏跨租户记录。
     */
    private StocktakeOrder findOrder(UUID tenantId, UUID id) {
        return repository.findById(tenantId, id)
                .orElseThrow(() -> new NotFoundException("盘点单不存在"));
    }

    /**
     * 获取可信操作人和请求上下文。
     */
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
     * 校验盘点创建字段。
     */
    private void validateCreateRequest(StocktakeCreateRequest request) {
        if (request == null || request.getWarehouseId() == null) {
            throw new ValidationException("盘点仓库不能为空");
        }
    }

    /**
     * 创建盘点单前校验仓库和可选库位的当前租户、ACTIVE 状态及库位归属。
     * 旧三参数构造器仅用于兼容纯单元测试，HTTP/Spring 生产构造器始终注入真实只读端口。
     */
    private void validateMasterDataReferences(StocktakeCreateRequest request, UUID tenantId) {
        if (warehouseReferencePort == null || inventoryLocationPort == null) {
            return;
        }
        if (!warehouseReferencePort.isActiveInTenant(tenantId, request.getWarehouseId())) {
            throw new com.ailearn.platform.shared.exception.NotFoundException("盘点仓库不存在");
        }
        if (request.getLocationId() == null) {
            return;
        }
        LocationSnapshot snapshot = inventoryLocationPort.findByTenantIdAndId(tenantId, request.getLocationId());
        if (snapshot == null || !tenantId.equals(snapshot.tenantId()) || !snapshot.isActive()
                || !request.getWarehouseId().equals(snapshot.warehouseId())) {
            throw new com.ailearn.platform.shared.exception.NotFoundException("盘点库位不存在");
        }
    }

    /**
     * 校验幂等键长度。
     */
    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    /**
     * 生成稳定载荷摘要。
     */
    private String digest(String operation, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation);
        payload.put("payload", value);
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ServiceUnavailableException("盘点幂等载荷摘要生成失败", exception);
        }
    }

    /**
     * 根据状态返回后端允许动作。
     */
    private List<com.ailearn.platform.core.masterdata.dto.AllowedActionVo> actions(StocktakeStatus status) {
        return switch (status) {
            case NotStarted -> List.of(new com.ailearn.platform.core.masterdata.dto.AllowedActionVo(
                    "start", true, null));
            case Counting -> List.of(new com.ailearn.platform.core.masterdata.dto.AllowedActionVo(
                    "confirm", true, null));
            case ConfirmedAdjusted -> List.of();
        };
    }

    /**
     * 可信盘点操作人。
     */
    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }
}
