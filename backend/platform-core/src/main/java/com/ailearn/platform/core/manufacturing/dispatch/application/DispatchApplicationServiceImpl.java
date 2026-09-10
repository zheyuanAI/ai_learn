package com.ailearn.platform.core.manufacturing.dispatch.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchRepository;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchCreateRequest;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchOrderView;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchPageQuery;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchPage;
import com.ailearn.platform.core.manufacturing.dispatch.exception.DispatchErrorCode;
import com.ailearn.platform.core.manufacturing.dispatch.exception.DispatchException;
import com.ailearn.platform.core.manufacturing.dispatch.port.WorkOrderReleasePort;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 派工应用服务；所有写操作使用可信租户/用户上下文和幂等执行器。 */
@Service
public class DispatchApplicationServiceImpl implements DispatchApplicationService {
    private final DispatchRepository repository;
    private final WorkOrderReleasePort workOrderReleasePort;
    private final CoreIdempotencyExecutor idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 使用内存幂等存储创建 focused 测试适配器。 */
    public DispatchApplicationServiceImpl(DispatchRepository repository,
                                          WorkOrderReleasePort workOrderReleasePort) {
        this(repository, workOrderReleasePort,
                new CoreIdempotencyExecutor(new InMemoryIdempotencyStorage(), defaultMapper()),
                defaultMapper(), Clock.systemUTC());
    }

    /** 生产装配构造器：使用 Core PostgreSQL 幂等存储，避免派工重启后丢失命令占用。 */
    @Autowired
    public DispatchApplicationServiceImpl(DispatchRepository repository,
                                          WorkOrderReleasePort workOrderReleasePort,
                                          IdempotencyStorage storage, ObjectMapper objectMapper) {
        this(repository, workOrderReleasePort, new CoreIdempotencyExecutor(storage, objectMapper),
                objectMapper, Clock.systemUTC());
    }

    /** 创建可替换时钟和幂等存储的应用服务。 */
    public DispatchApplicationServiceImpl(DispatchRepository repository,
                                          WorkOrderReleasePort workOrderReleasePort,
                                          CoreIdempotencyExecutor idempotency,
                                          ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.workOrderReleasePort = workOrderReleasePort;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public DispatchOrder create(DispatchCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        validate(request, idempotencyKey);
        return idempotency.execute("mes:dispatch:create", actor.tenantId(), idempotencyKey,
                digest(request), DispatchOrder.class, () -> {
                    workOrderReleasePort.lockWorkOrder(actor.tenantId(), request.workOrderId());
                    requireReleased(actor.tenantId(), request.workOrderId());
                    requireOperationInRouting(actor, request.operationId(), request.workOrderId());
                    checkCumulativeQuantity(actor.tenantId(), request.workOrderId(), request.dispatchQty());
                    return repository.saveIfAbsent(DispatchOrder.draft(UUID.randomUUID(), actor.tenantId(),
                            request.workOrderId(), request.operationId(), request.operatorId(), request.dispatchQty(),
                            request.deviceId(), actor.userId(), now()));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public DispatchOrder release(UUID dispatchId, String idempotencyKey) {
        return transition(dispatchId, idempotencyKey, "release", DispatchOrder::released);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public DispatchOrder startProcessing(UUID dispatchId, String idempotencyKey) {
        return transition(dispatchId, idempotencyKey, "start", DispatchOrder::processing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public DispatchOrder complete(UUID dispatchId, String idempotencyKey) {
        return transition(dispatchId, idempotencyKey, "complete", DispatchOrder::completed);
    }

    @Override
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public Optional<DispatchOrder> find(UUID dispatchId) {
        return repository.find(TenantContextHolder.requireTenantId(), dispatchId);
    }

    @Override
    @PreAuthorize("hasAuthority('mes:dispatch:manage')")
    public DispatchPage page(DispatchPageQuery query) {
        Actor actor = actor();
        DispatchPageQuery normalized = query == null ? new DispatchPageQuery() : query.normalized();
        List<DispatchOrder> filtered = repository.findAll(actor.tenantId()).stream()
                .filter(value -> normalized.getWorkOrderId() == null
                        || normalized.getWorkOrderId().equals(value.workOrderId()))
                .filter(value -> normalized.getOperationId() == null
                        || normalized.getOperationId().equals(value.operationId()))
                .filter(value -> normalized.getOperatorId() == null
                        || normalized.getOperatorId().equals(value.operatorId()))
                .filter(value -> normalized.getStatus() == null
                        || normalized.getStatus().equalsIgnoreCase(value.status().name()))
                .toList();
        int from = Math.min((normalized.getPage() - 1) * normalized.getSize(), filtered.size());
        int to = Math.min(from + normalized.getSize(), filtered.size());
        // 修改用途：列表不再直接暴露内部派工聚合，统一返回页面字段和服务端动作能力。
        List<DispatchOrderView> views = filtered.subList(from, to).stream()
                .map(DispatchOrderView::from)
                .toList();
        return new DispatchPage(views, filtered.size(), normalized.getPage(), normalized.getSize());
    }

    private DispatchOrder transition(UUID id, String key, String operation,
                                     Transition transition) {
        Actor actor = actor();
        if (id == null || key == null || key.isBlank()) {
            throw new DispatchException(DispatchErrorCode.MES_DISPATCH_001, "缺少派工单或幂等键");
        }
        DispatchOrder current = required(actor.tenantId(), id);
        return idempotency.execute("mes:dispatch:" + operation, actor.tenantId(), key,
                digest(id), DispatchOrder.class, () -> {
                    requireOperationInRouting(actor, current.operationId(), current.workOrderId());
                    if (operation.equals("release") || operation.equals("start")) {
                        requireReleased(actor.tenantId(), current.workOrderId());
                    }
                    DispatchOrder updated = repository.update(actor.tenantId(), id,
                            value -> transition.apply(value, actor.userId(), now()));
                    return updated == null ? required(actor.tenantId(), id) : updated;
                });
    }

    private DispatchOrder required(UUID tenantId, UUID id) {
        return repository.find(tenantId, id).orElseThrow(() ->
                new DispatchException(DispatchErrorCode.MES_DISPATCH_003, "派工单不存在"));
    }

    private void requireReleased(UUID tenantId, UUID workOrderId) {
        if (!workOrderReleasePort.isReleased(tenantId, workOrderId)) {
            throw new DispatchException(DispatchErrorCode.MES_DISPATCH_002,
                    "工单必须处于 Released 状态");
        }
    }

    /** 校验派工工序属于工单冻结 Routing，避免把任意 UUID 写成生产安排。 */
    private void requireOperationInRouting(Actor actor, UUID operationId, UUID workOrderId) {
        if (!workOrderReleasePort.isOperationInRouting(actor.tenantId(), workOrderId, operationId)) {
            throw new DispatchException(DispatchErrorCode.MES_DISPATCH_005,
                    "派工工序不属于工单冻结 Routing");
        }
    }

    /** 在工单行锁保护下校验所有有效派工的累计数量不超过工单计划数量。 */
    private void checkCumulativeQuantity(UUID tenantId, UUID workOrderId, BigDecimal requested) {
        workOrderReleasePort.plannedQty(tenantId, workOrderId).ifPresent(planned -> {
            BigDecimal existing = repository.findByWorkOrder(tenantId, workOrderId).stream()
                    .map(DispatchOrder::dispatchQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (existing.add(requested).compareTo(planned) > 0) {
                throw new DispatchException(DispatchErrorCode.MES_DISPATCH_006,
                        "累计派工数量不能超过工单计划数量");
            }
        });
    }

    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = UserContextHolder.getSessionId();
        String requestId = RequestContextHolder.getRequestId();
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            throw new DispatchException(DispatchErrorCode.MES_DISPATCH_001,
                    "缺少可信会话或请求上下文");
        }
        return new Actor(tenantId, userId);
    }

    private void validate(DispatchCreateRequest request, String key) {
        if (request == null || request.workOrderId() == null || request.operationId() == null
                || request.operatorId() == null || request.dispatchQty() == null
                || request.dispatchQty().signum() <= 0
                || key == null || key.isBlank()) {
            throw new DispatchException(DispatchErrorCode.MES_DISPATCH_001,
                    "工单、工序、操作员、派工数量或幂等键不合法");
        }
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }

    private String digest(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("派工幂等摘要生成失败", exception);
        }
    }

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @FunctionalInterface
    private interface Transition {
        DispatchOrder apply(DispatchOrder current, UUID userId, OffsetDateTime at);
    }

    private record Actor(UUID tenantId, UUID userId) { }
}
