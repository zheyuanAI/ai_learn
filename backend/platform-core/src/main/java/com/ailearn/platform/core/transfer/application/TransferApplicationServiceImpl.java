package com.ailearn.platform.core.transfer.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.masterdata.domain.port.WarehouseReferencePort;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
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
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ValidationException;
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
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 调拨应用服务实现。
 * <p>
 * 该服务只持久化调拨单状态，确认时把每条明细转换为库存移动命令；不直接依赖库存 Mapper 或余额表。
 * </p>
 */
@Service
public class TransferApplicationServiceImpl implements TransferApplicationService {

    private final TransferRepository repository;
    private final InventoryCommandService inventoryCommandService;
    private final CoreIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;
    private final WarehouseReferencePort warehouseReferencePort;
    private final InventoryLocationPort inventoryLocationPort;

    /**
     * 提供纯单元测试使用的内存幂等构造器。
     *
     * @param repository 调拨持久化端口
     * @param inventoryCommandService 唯一库存写端口
     */
    public TransferApplicationServiceImpl(TransferRepository repository,
                                           InventoryCommandService inventoryCommandService) {
        this(repository, inventoryCommandService, new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()), null, null);
    }

    /**
     * 创建可替换幂等存储的调拨服务。
     *
     * @param repository 调拨持久化端口
     * @param inventoryCommandService 唯一库存写端口
     * @param storage Core 幂等存储
     * @param objectMapper 结果序列化器
     */
    @Autowired
    public TransferApplicationServiceImpl(TransferRepository repository,
                                           InventoryCommandService inventoryCommandService,
                                           IdempotencyStorage storage,
                                           ObjectMapper objectMapper,
                                           WarehouseReferencePort warehouseReferencePort,
                                           InventoryLocationPort inventoryLocationPort) {
        this.repository = repository;
        this.inventoryCommandService = inventoryCommandService;
        this.objectMapper = objectMapper;
        this.idempotencyExecutor = new CoreIdempotencyExecutor(storage, objectMapper);
        this.warehouseReferencePort = warehouseReferencePort;
        this.inventoryLocationPort = inventoryLocationPort;
    }

    /**
     * 创建调拨草稿。
     * 入参：业务字段和 HTTP 幂等键；出参：草稿及允许动作；流程：校验可信操作人、解析明细、在租户内落库，
     * 同载荷重试返回首次结果。
     *
     * @param request 创建请求
     * @param idempotencyKey 幂等键
     * @return 调拨草稿
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('inv:transfer:create')")
    public TransferView create(TransferCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateCreateRequest(request);
        validateMasterDataReferences(request, actor.tenantId());
        return idempotencyExecutor.execute("transfer:create", actor.tenantId(), idempotencyKey,
                digest("create", request),
                TransferView.class, () -> createInternal(request, actor));
    }

    /**
     * 确认调拨并逐行调用库存移动。
     * 入参：当前租户调拨单 ID 和幂等键；出参：确认状态、版本及库存流水 ID；流程：加载草稿 -> 每行调用
     * InventoryCommandService.move -> 版本条件确认，任何一步失败由事务回滚。
     *
     * @param id 调拨单 ID
     * @param idempotencyKey 幂等键
     * @return 已确认调拨
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('inv:transfer:confirm')")
    public TransferView confirm(UUID id, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (id == null) {
            throw new ValidationException("调拨单 ID 不能为空");
        }
        return idempotencyExecutor.execute("transfer:confirm", actor.tenantId(), idempotencyKey,
                digest("confirm", id),
                TransferView.class, () -> confirmInternal(id, actor));
    }

    /**
     * 创建调拨聚合并保存草稿。
     */
    private TransferView createInternal(TransferCreateRequest request, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID orderId = UUID.randomUUID();
        String transferNo = request.getTransferNo() == null || request.getTransferNo().isBlank()
                ? "TR-" + orderId : request.getTransferNo().trim();
        List<TransferLine> lines = new ArrayList<>();
        int lineNo = 1;
        for (TransferLineRequest lineRequest : request.getLines()) {
            if (lineRequest == null) {
                throw new ValidationException("调拨明细不能为空");
            }
            lines.add(new TransferLine(UUID.randomUUID(), actor.tenantId(), lineNo++, lineRequest.getProductId(),
                    lineRequest.getLotNo(), lineRequest.getUom(), parseQuantity(lineRequest.getQuantity())));
        }
        TransferOrder order = new TransferOrder(orderId, actor.tenantId(), transferNo,
                request.getFromWarehouseId(), request.getFromLocationId(), request.getToWarehouseId(),
                request.getToLocationId(), TransferStatus.Draft, 0L, null, null, actor.userId(), now,
                actor.userId(), now, lines);
        return new TransferView(repository.insert(order), List.of(), draftActions());
    }

    /**
     * 执行调拨库存移动并推进调拨聚合版本。
     */
    private TransferView confirmInternal(UUID id, Actor actor) {
        TransferOrder order = repository.findById(actor.tenantId(), id)
                .orElseThrow(() -> new NotFoundException("调拨单不存在"));
        if (order.status() != TransferStatus.Draft) {
            throw new ConflictException("调拨单当前状态不允许确认");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UUID> transactionIds = new ArrayList<>();
        for (TransferLine line : order.lines()) {
            InventoryDimension from = new InventoryDimension(line.productId(), order.fromWarehouseId(),
                    order.fromLocationId(), line.lotNo());
            InventoryDimension to = new InventoryDimension(line.productId(), order.toWarehouseId(),
                    order.toLocationId(), line.lotNo());
            String lineKey = "transfer:" + order.id() + ":line:" + line.id();
            InventoryCommandMetadata metadata = new InventoryCommandMetadata(actor.tenantId(), actor.userId(),
                    actor.sessionId(), actor.requestId(), lineKey, digest("transfer-line", line.id()),
                    "TRANSFER", order.id(), line.id(), "TRANSFER", now);
            var result = inventoryCommandService.move(new com.ailearn.platform.core.inventory.application.InventoryMoveCommand(
                    metadata, from, to, line.quantity()));
            transactionIds.addAll(result.transactions().stream().map(InventoryTransaction::id).toList());
        }
        if (!repository.confirm(actor.tenantId(), order.id(), order.version(), actor.userId(), now)) {
            throw new ConflictException("调拨版本已变化，请重新读取");
        }
        return new TransferView(order.confirmed(actor.userId(), now), transactionIds, List.of());
    }

    /**
     * 构造可信操作人，拒绝没有会话 JTI 或请求 ID 的后台写入。
     */
    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = UserContextHolder.getSessionId();
        String requestId = RequestContextHolder.getRequestId();
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            throw new com.ailearn.platform.shared.exception.ForbiddenException("缺失可信会话或请求上下文");
        }
        return new Actor(tenantId, userId, sessionId, requestId);
    }

    /**
     * 校验创建请求的结构边界。
     */
    private void validateCreateRequest(TransferCreateRequest request) {
        if (request == null || request.getFromWarehouseId() == null || request.getFromLocationId() == null
                || request.getToWarehouseId() == null || request.getToLocationId() == null
                || request.getLines() == null || request.getLines().isEmpty()) {
            throw new ValidationException("调拨单仓库、库位和明细不能为空");
        }
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new ValidationException("调拨来源和目标库位不能相同");
        }
    }

    /**
     * 创建草稿前校验仓库和库位的当前租户、ACTIVE 状态及库位归属；旧双参数测试构造器不具备引用端口，
     * 仅作为内部兼容入口保留，HTTP/Spring 生产构造器始终注入真实只读端口。
     */
    private void validateMasterDataReferences(TransferCreateRequest request, UUID tenantId) {
        if (warehouseReferencePort == null || inventoryLocationPort == null) {
            return;
        }
        requireActiveWarehouse(tenantId, request.getFromWarehouseId());
        requireActiveWarehouse(tenantId, request.getToWarehouseId());
        requireActiveLocation(tenantId, request.getFromLocationId(), request.getFromWarehouseId());
        requireActiveLocation(tenantId, request.getToLocationId(), request.getToWarehouseId());
    }

    private void requireActiveWarehouse(UUID tenantId, UUID warehouseId) {
        if (!warehouseReferencePort.isActiveInTenant(tenantId, warehouseId)) {
            throw new NotFoundException("调拨仓库不存在");
        }
    }

    private void requireActiveLocation(UUID tenantId, UUID locationId, UUID warehouseId) {
        LocationSnapshot snapshot = inventoryLocationPort.findByTenantIdAndId(tenantId, locationId);
        if (snapshot == null || !tenantId.equals(snapshot.tenantId()) || !snapshot.isActive()
                || !warehouseId.equals(snapshot.warehouseId())) {
            throw new NotFoundException("调拨库位不存在");
        }
    }

    /**
     * 解析数量字符串并把格式错误转换为受控校验异常。
     */
    private BigDecimal parseQuantity(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("调拨数量不能为空");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new ValidationException("调拨数量格式不正确");
        }
    }

    /**
     * 校验 HTTP 幂等键长度。
     */
    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    /**
     * 生成稳定 SHA-256 载荷摘要。
     */
    private String digest(String operation, Object value) {
        try {
            byte[] payload = objectMapper.writeValueAsString(List.of(operation, value)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ValidationException("调拨幂等载荷摘要生成失败");
        }
    }

    /**
     * 草稿允许动作。
     */
    private List<AllowedActionVo> draftActions() {
        return List.of(new AllowedActionVo("confirm", true, null));
    }

    /**
     * 调拨可信操作人上下文。
     */
    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }
}
