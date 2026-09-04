package com.ailearn.platform.core.manufacturing.operation.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchStatus;
import com.ailearn.platform.core.manufacturing.dispatch.port.DispatchReferencePort;
import com.ailearn.platform.core.manufacturing.dispatch.port.WorkOrderReleasePort;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionStatus;
import com.ailearn.platform.core.manufacturing.operation.dto.OperationExecutionCreateRequest;
import com.ailearn.platform.core.manufacturing.operation.exception.OperationExecutionErrorCode;
import com.ailearn.platform.core.manufacturing.operation.exception.OperationExecutionException;
import com.ailearn.platform.core.manufacturing.execution.application.WorkOrderExecutionService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 工序执行应用服务；以工单和派工内部端口校验边界，不直接写工单或 IoT。 */
@Service
public class OperationExecutionApplicationServiceImpl implements OperationExecutionApplicationService {
    private final OperationExecutionRepository repository;
    private final DispatchReferencePort dispatchReferencePort;
    private final WorkOrderReleasePort workOrderReleasePort;
    private final WorkOrderExecutionService workOrderExecutionService;
    private final CoreIdempotencyExecutor idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 使用内存幂等存储创建 focused 测试适配器。 */
    public OperationExecutionApplicationServiceImpl(OperationExecutionRepository repository,
                                                    DispatchReferencePort dispatchReferencePort,
                                                    WorkOrderReleasePort workOrderReleasePort) {
        this(repository, dispatchReferencePort, workOrderReleasePort, null,
                new CoreIdempotencyExecutor(new InMemoryIdempotencyStorage(), defaultMapper()),
                defaultMapper(), Clock.systemUTC());
    }

    /** 生产装配构造器：工序开始/完成时同步工单执行生命周期，避免两套状态各自漂移。 */
    public OperationExecutionApplicationServiceImpl(OperationExecutionRepository repository,
                                                    DispatchReferencePort dispatchReferencePort,
                                                    WorkOrderReleasePort workOrderReleasePort,
                                                    WorkOrderExecutionService workOrderExecutionService) {
        this(repository, dispatchReferencePort, workOrderReleasePort, workOrderExecutionService,
                new CoreIdempotencyExecutor(new InMemoryIdempotencyStorage(), defaultMapper()),
                defaultMapper(), Clock.systemUTC());
    }

    /** 生产装配构造器：使用 Core 持久化幂等存储并同步工单生命周期。 */
    @Autowired
    public OperationExecutionApplicationServiceImpl(OperationExecutionRepository repository,
                                                    DispatchReferencePort dispatchReferencePort,
                                                    WorkOrderReleasePort workOrderReleasePort,
                                                    WorkOrderExecutionService workOrderExecutionService,
                                                    IdempotencyStorage storage, ObjectMapper objectMapper) {
        this(repository, dispatchReferencePort, workOrderReleasePort, workOrderExecutionService,
                new CoreIdempotencyExecutor(storage, objectMapper), objectMapper, Clock.systemUTC());
    }

    /** 创建可替换时钟和幂等存储的工序应用服务。 */
    public OperationExecutionApplicationServiceImpl(OperationExecutionRepository repository,
                                                    DispatchReferencePort dispatchReferencePort,
                                                    WorkOrderReleasePort workOrderReleasePort,
                                                    CoreIdempotencyExecutor idempotency,
                                                    ObjectMapper objectMapper, Clock clock) {
        this(repository, dispatchReferencePort, workOrderReleasePort, null, idempotency, objectMapper, clock);
    }

    /** 创建可替换时钟、幂等器和工单生命周期端口的完整构造器。 */
    public OperationExecutionApplicationServiceImpl(OperationExecutionRepository repository,
                                                    DispatchReferencePort dispatchReferencePort,
                                                    WorkOrderReleasePort workOrderReleasePort,
                                                    WorkOrderExecutionService workOrderExecutionService,
                                                    CoreIdempotencyExecutor idempotency,
                                                    ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.dispatchReferencePort = dispatchReferencePort;
        this.workOrderReleasePort = workOrderReleasePort;
        this.workOrderExecutionService = workOrderExecutionService;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public OperationExecution create(OperationExecutionCreateRequest request, String idempotencyKey) {
        Actor actor = actor();
        validate(request, idempotencyKey);
        return idempotency.execute("mes:operation:create", actor.tenantId(), idempotencyKey,
                digest(request), OperationExecution.class, () -> {
                    DispatchOrder dispatch = dispatchReferencePort.find(actor.tenantId(), request.dispatchId())
                            .orElseThrow(() -> new OperationExecutionException(
                                    OperationExecutionErrorCode.MES_OPERATION_005, "派工单不存在"));
                    if (dispatch.status() != DispatchStatus.Released
                            || (request.workOrderId() != null && !request.workOrderId().equals(dispatch.workOrderId()))
                            || (request.operationId() != null && !request.operationId().equals(dispatch.operationId()))) {
                        throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_005,
                                "派工单必须为 Released 且与工单/工序一致");
                    }
                    requireReleased(actor.tenantId(), dispatch.workOrderId());
                    return repository.saveIfAbsent(OperationExecution.notStarted(UUID.randomUUID(),
                            actor.tenantId(), request.dispatchId(), dispatch.workOrderId(),
                            dispatch.operationId(), request.deviceId() == null ? dispatch.deviceId() : request.deviceId()));
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public OperationExecution start(UUID executionId, OffsetDateTime occurredAt, String idempotencyKey) {
        Actor actor = actor();
        return transition(executionId, occurredAt, null, idempotencyKey, "start",
                (current, user, at) -> {
                    requireReleased(actor.tenantId(), current.workOrderId());
                    ensureDeviceHasNoOtherActive(current);
                    if (workOrderExecutionService != null) {
                        workOrderExecutionService.startProduction(current.workOrderId(),
                                "mes-operation-start-" + current.id());
                    }
                    return current.start(user, at);
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public OperationExecution pause(UUID executionId, String reason, OffsetDateTime occurredAt,
                                    String idempotencyKey) {
        actor();
        if (reason == null || reason.isBlank()) {
            throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_001,
                    "暂停原因不能为空");
        }
        return transition(executionId, occurredAt, reason, idempotencyKey, "pause",
                (current, user, at) -> current.pause(reason, user, at));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public OperationExecution resume(UUID executionId, OffsetDateTime occurredAt, String idempotencyKey) {
        actor();
        return transition(executionId, occurredAt, null, idempotencyKey, "resume",
                (current, user, at) -> current.resume(user, at));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public OperationExecution complete(UUID executionId, OffsetDateTime occurredAt, String idempotencyKey) {
        actor();
        return transition(executionId, occurredAt, null, idempotencyKey, "complete",
                (current, user, at) -> {
                    OperationExecution completed = current.complete(user, at);
                    synchronizeWorkOrderCompletion(completed);
                    return completed;
                });
    }

    @Override
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public Optional<OperationExecution> find(UUID executionId) {
        return repository.find(TenantContextHolder.requireTenantId(), executionId);
    }

    /**
     * 执行带事件语义的状态迁移，并把发生时间、暂停原因纳入幂等摘要。
     *
     * @param executionId 工序执行标识
     * @param occurredAt 客户端记录的事件发生时间
     * @param reason 暂停原因；其他动作为空
     * @param key HTTP 幂等键
     * @param operation 状态动作
     * @param transition 领域状态迁移函数
     * @return 迁移后的工序执行聚合
     */
    private OperationExecution transition(UUID executionId, OffsetDateTime occurredAt, String reason,
                                          String key, String operation, Transition transition) {
        Actor actor = actor();
        if (executionId == null || occurredAt == null || key == null || key.isBlank()) {
            throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_001,
                    "执行标识、事件时间或幂等键不合法");
        }
        OperationExecution current = required(actor.tenantId(), executionId);
        synchronized (repository) {
            return idempotency.execute("mes:operation:" + operation, actor.tenantId(), key,
                    digest(new TransitionPayload(executionId, operation, occurredAt, reason)),
                    OperationExecution.class, () -> {
                        try {
                            OperationExecution updated = repository.update(actor.tenantId(), executionId,
                                    value -> transition.apply(value, actor.userId(), occurredAt));
                            return updated == null ? required(actor.tenantId(), executionId) : updated;
                        } catch (IllegalStateException | IllegalArgumentException exception) {
                            throw new OperationExecutionException(
                                    OperationExecutionErrorCode.MES_OPERATION_003, exception.getMessage());
                        }
                    });
        }
    }

    private void ensureDeviceHasNoOtherActive(OperationExecution current) {
        if (current.deviceId() == null) {
            return;
        }
        boolean occupied = repository.findByDevice(current.tenantId(), current.deviceId()).stream()
                .filter(value -> !value.id().equals(current.id()))
                .anyMatch(value -> value.status() == OperationExecutionStatus.Running
                        || value.status() == OperationExecutionStatus.Paused);
        if (occupied) {
            throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_006,
                    "设备已有活动工序");
        }
    }

    /** 工序完成后把该工序加入工单生命周期快照，供工单正常完成判断使用。 */
    private void synchronizeWorkOrderCompletion(OperationExecution execution) {
        if (workOrderExecutionService == null) {
            return;
        }
        WorkOrderLifecycle lifecycle = workOrderExecutionService.find(execution.workOrderId()).orElseThrow(() ->
                new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_005,
                        "工单生命周期不存在或不属于当前租户"));
        Set<UUID> completedOperations = new HashSet<>(lifecycle.progress().completedOperationIds());
        completedOperations.add(execution.operationId());
        WorkOrderProgress current = lifecycle.progress();
        workOrderExecutionService.recordProgress(execution.workOrderId(),
                new WorkOrderProgress(completedOperations, current.reportedQty(), current.qualifiedQty(),
                        current.defectQty(), current.receivedQty(), current.qualityBlocked(),
                        current.pendingInventoryCommands()),
                "mes-operation-progress-" + execution.id() + "-" + execution.version());
    }

    private OperationExecution required(UUID tenantId, UUID id) {
        return repository.find(tenantId, id).orElseThrow(() ->
                new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_002,
                        "工序执行记录不存在"));
    }

    private void requireReleased(UUID tenantId, UUID workOrderId) {
        if (!workOrderReleasePort.isReleased(tenantId, workOrderId)) {
            throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_004,
                    "工单必须处于 Released 状态");
        }
    }

    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = UserContextHolder.getSessionId();
        String requestId = RequestContextHolder.getRequestId();
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_001,
                    "缺少可信会话或请求上下文");
        }
        return new Actor(tenantId, userId);
    }

    private void validate(OperationExecutionCreateRequest request, String key) {
        if (request == null || request.dispatchId() == null || key == null || key.isBlank()) {
            throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_001,
                    "工序执行字段或幂等键不合法");
        }
    }

    private String digest(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("工序执行幂等摘要生成失败", exception);
        }
    }

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /** 状态迁移幂等摘要载荷，避免同一键提交不同事件语义时被错误重放。 */
    private record TransitionPayload(UUID executionId, String operation,
                                     OffsetDateTime occurredAt, String reason) { }

    @FunctionalInterface
    private interface Transition {
        OperationExecution apply(OperationExecution current, UUID userId, OffsetDateTime at);
    }

    private record Actor(UUID tenantId, UUID userId) { }
}
