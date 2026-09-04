package com.ailearn.platform.core.sales.application;

import com.ailearn.platform.core.masterdata.domain.entity.Customer;
import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderPage;
import com.ailearn.platform.core.sales.domain.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderLineRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderPageResult;
import com.ailearn.platform.core.sales.dto.SalesOrderSaveRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import com.ailearn.platform.core.sales.exception.SalesOrderErrorCode;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
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
 * 销售订单基础应用服务。
 * <p>
 * 本服务只管理订单聚合、双轴状态和订单行累计数量，不直接操作库存表，也不调用库存履约命令；
 * 预留、拣货、退回、释放和发货由 Task 13 通过独立履约边界接入。
 * </p>
 */
@Service
public class SalesOrderApplicationServiceImpl implements SalesOrderApplicationService {

    private final SalesOrderRepository repository;
    private final MasterDataRepository<Customer> customerRepository;
    private final MasterDataRepository<Product> productRepository;
    private final SalesOrderIdempotencyExecutor idempotencyExecutor;
    private final ObjectMapper objectMapper;

    /**
     * 提供纯单元测试使用的内存幂等构造器。
     *
     * @param repository 销售订单持久化端口
     * @param customerRepository 客户主数据端口
     * @param productRepository 产品主数据端口
     */
    public SalesOrderApplicationServiceImpl(SalesOrderRepository repository,
                                            MasterDataRepository<Customer> customerRepository,
                                            MasterDataRepository<Product> productRepository) {
        this(repository, customerRepository, productRepository,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建可替换幂等存储的销售订单服务。
     *
     * @param repository 销售订单持久化端口
     * @param customerRepository 客户主数据端口
     * @param productRepository 产品主数据端口
     * @param storage Core 幂等存储
     * @param objectMapper 结果序列化器
     */
    @Autowired
    public SalesOrderApplicationServiceImpl(SalesOrderRepository repository,
                                            MasterDataRepository<Customer> customerRepository,
                                            MasterDataRepository<Product> productRepository,
                                            IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
        this.idempotencyExecutor = new SalesOrderIdempotencyExecutor(storage, objectMapper);
    }

    /**
     * 查询当前租户订单，并按后端事实计算履约状态和允许动作。
     *
     * @param query 查询条件
     * @return 当前租户分页结果
     */
    @Override
    @PreAuthorize("hasAuthority('sales:order:view')")
    public SalesOrderPageResult page(com.ailearn.platform.core.sales.dto.SalesOrderPageQuery query) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        SalesOrderPage page = repository.findPage(tenantId,
                query == null ? new SalesOrderPageQuery(null, null, null, null, 1, 20) : query.normalized());
        return new SalesOrderPageResult(page.records().stream().map(this::toView).toList(),
                page.total(), page.page(), page.size());
    }

    /**
     * 查询当前租户订单详情；跨租户 ID 按不存在处理。
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    @Override
    @PreAuthorize("hasAuthority('sales:order:view')")
    public SalesOrderView detail(UUID id) {
        return toView(findOrder(TenantContextHolder.requireTenantId(), id));
    }

    /**
     * 创建 Draft 销售订单。
     * 入参：订单草稿和幂等键；出参：Draft 详情；流程：可信上下文 -> 客户/产品同租户校验 -> 聚合持久化。
     *
     * @param request 创建请求
     * @param idempotencyKey HTTP Idempotency-Key
     * @return Draft 订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:order:create')")
    public SalesOrderView create(SalesOrderSaveRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateSaveRequest(request, true);
        return idempotencyExecutor.execute("sales:order:create", actor.tenantId(), idempotencyKey,
                digest("create", request), SalesOrderView.class,
                () -> toView(repository.insert(buildNewOrder(request, actor))));
    }

    /**
     * 修改 Draft 订单，提交后所有核心字段冻结。
     *
     * @param id 订单 ID
     * @param request 完整草稿请求
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 修改后的订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:order:update')")
    public SalesOrderView update(UUID id, SalesOrderSaveRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        validateSaveRequest(request, false);
        return idempotencyExecutor.execute("sales:order:update", actor.tenantId(), idempotencyKey,
                digest("update", List.of(id, request)), SalesOrderView.class,
                () -> updateInternal(id, request, actor));
    }

    /**
     * 将 Draft 提交为 Submitted。
     *
     * @param id 订单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 已提交订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:order:submit')")
    public SalesOrderView submit(UUID id, String idempotencyKey) {
        return transition("sales:order:submit", id, idempotencyKey,
                (order, actor, now) -> order.submit(actor.userId(), now));
    }

    /**
     * 将 Submitted 审核为 Approved；审核不自动预留库存。
     *
     * @param id 订单 ID
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 已审核订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:order:approve')")
    public SalesOrderView approve(UUID id, String idempotencyKey) {
        return transition("sales:order:approve", id, idempotencyKey,
                (order, actor, now) -> order.approve(actor.userId(), now));
    }

    /**
     * 人工完成 Approved 订单，记录原因、用户、会话 JTI 和时间；不生成拣货、发货或库存事实。
     *
     * @param id 订单 ID
     * @param request 人工完成原因
     * @param idempotencyKey HTTP Idempotency-Key
     * @return 已完成人工订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:order:complete')")
    public SalesOrderView manuallyComplete(UUID id, SalesOrderCompleteRequest request, String idempotencyKey) {
        Actor actor = actor();
        validateKey(idempotencyKey);
        if (request == null || request.getCompletionReason() == null
                || request.getCompletionReason().trim().isBlank()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "人工完成原因不能为空");
        }
        return idempotencyExecutor.execute("sales:order:complete", actor.tenantId(), idempotencyKey,
                digest("manual-complete", List.of(id, request.getCompletionReason().trim())),
                SalesOrderView.class, () -> {
                    SalesOrder order = findOrder(actor.tenantId(), id);
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    SalesOrder completed = order.manuallyComplete(request.getCompletionReason(), actor.userId(),
                            actor.sessionId(), now);
                    return toView(repository.updateState(completed, order.version()));
                });
    }

    private SalesOrderView updateInternal(UUID id, SalesOrderSaveRequest request, Actor actor) {
        SalesOrder order = findOrder(actor.tenantId(), id);
        List<SalesOrderLine> lines = buildLines(request.getLines(), actor.tenantId());
        SalesOrder updated = order.draftUpdated(request.getCustomerId(), request.getPlannedShipDate(),
                normalizeRemark(request.getRemark()), lines, actor.userId(), OffsetDateTime.now(ZoneOffset.UTC));
        return toView(repository.update(updated, order.version()));
    }

    private SalesOrderView transition(String operation, UUID id, String key, Transition transition) {
        Actor actor = actor();
        validateKey(key);
        return idempotencyExecutor.execute(operation, actor.tenantId(), key, digest(operation, id),
                SalesOrderView.class, () -> {
                    SalesOrder order = findOrder(actor.tenantId(), id);
                    SalesOrder next = transition.apply(order, actor, OffsetDateTime.now(ZoneOffset.UTC));
                    return toView(repository.updateState(next, order.version()));
                });
    }

    private SalesOrder buildNewOrder(SalesOrderSaveRequest request, Actor actor) {
        validateCustomer(actor.tenantId(), request.getCustomerId());
        List<SalesOrderLine> lines = buildLines(request.getLines(), actor.tenantId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new SalesOrder(UUID.randomUUID(), actor.tenantId(), request.getSoNo().trim(), request.getCustomerId(),
                request.getPlannedShipDate(), SalesOrderStatus.Draft, null, null, null, null, null,
                normalizeRemark(request.getRemark()), 0L, actor.userId(), now, actor.userId(), now, lines);
    }

    private List<SalesOrderLine> buildLines(List<SalesOrderLineRequest> requests, UUID tenantId) {
        return requests.stream().map(request -> {
            if (request == null || request.getLineNo() == null || request.getProductId() == null
                    || request.getUom() == null || request.getUom().isBlank()) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单明细必要字段不能为空");
            }
            Product product = productRepository.findById(tenantId, request.getProductId())
                    .orElseThrow(() -> new SalesOrderException(SalesOrderErrorCode.SO_004, "产品不存在或不属于当前租户"));
            if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_004, "产品已停用");
            }
            if (product.getUom() == null || product.getUom().isBlank()) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_004, "产品计量单位不可用");
            }
            if (!product.getUom().equals(request.getUom().trim())) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_006, "订单行计量单位与产品主数据不一致");
            }
            return new SalesOrderLine(UUID.randomUUID(), tenantId, request.getLineNo(), request.getProductId(),
                    request.getUom().trim(), parsePositive(request.getOrderedQty(), "orderedQty"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }).toList();
    }

    private void validateSaveRequest(SalesOrderSaveRequest request, boolean creating) {
        if (request == null || (creating && (request.getSoNo() == null || request.getSoNo().trim().isBlank()))
                || request.getCustomerId() == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单号、客户和明细不能为空");
        }
        if (request.getSoNo() != null && request.getSoNo().trim().length() > 64) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_006, "销售订单号不能超过 64 个字符");
        }
        normalizeRemark(request.getRemark());
    }

    private void validateCustomer(UUID tenantId, UUID customerId) {
        Customer customer = customerRepository.findById(tenantId, customerId)
                .orElseThrow(() -> new SalesOrderException(SalesOrderErrorCode.SO_004, "客户不存在或不属于当前租户"));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_004, "客户已停用");
        }
    }

    private BigDecimal parsePositive(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 必须大于 0");
        }
        try {
            BigDecimal quantity = new BigDecimal(value.trim());
            if (quantity.signum() <= 0) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 必须大于 0");
            }
            return quantity;
        } catch (NumberFormatException exception) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 格式不正确");
        }
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalized = remark.trim();
        if (normalized.length() > 512) {
            throw new ValidationException("remark 不能超过 512 个字符");
        }
        return normalized;
    }

    private SalesOrder findOrder(UUID tenantId, UUID id) {
        if (id == null) {
            throw new NotFoundException("销售订单不存在");
        }
        return repository.findById(tenantId, id).orElseThrow(() -> new NotFoundException("销售订单不存在"));
    }

    private SalesOrderView toView(SalesOrder order) {
        if (order == null) {
            throw new ServiceUnavailableException("销售订单持久化结果为空");
        }
        return new SalesOrderView(order, actions(order));
    }

    private List<com.ailearn.platform.core.masterdata.dto.AllowedActionVo> actions(SalesOrder order) {
        return switch (order.status()) {
            case Draft -> List.of(action("update", true), action("submit", true));
            case Submitted -> List.of(action("approve", true));
            case Approved -> List.of(action("directPick", order.lines().stream()
                            .anyMatch(line -> line.unshippedQty().signum() > 0)),
                    action("ship", order.lines().stream().anyMatch(line -> line.shippingStagedQty().signum() > 0)),
                    action("manualComplete", order.lines().stream()
                            .noneMatch(line -> line.shippingStagedQty().signum() > 0)));
            case Completed -> List.of();
        };
    }

    private com.ailearn.platform.core.masterdata.dto.AllowedActionVo action(String name, boolean allowed) {
        return new com.ailearn.platform.core.masterdata.dto.AllowedActionVo(name, allowed,
                allowed ? null : "当前订单数量或状态不允许此操作");
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

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
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
            throw new ServiceUnavailableException("销售订单幂等载荷摘要生成失败", exception);
        }
    }

    @FunctionalInterface
    private interface Transition {
        SalesOrder apply(SalesOrder order, Actor actor, OffsetDateTime now);
    }

    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }
}
