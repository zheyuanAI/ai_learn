package com.ailearn.platform.core.manufacturing.execution.application;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycleRepository;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.execution.exception.WorkOrderExecutionErrorCode;
import com.ailearn.platform.core.manufacturing.execution.exception.WorkOrderExecutionException;
import com.ailearn.platform.core.manufacturing.foundation.application.FoundationIdempotencyExecutor;
import com.ailearn.platform.core.manufacturing.foundation.application.ManufacturingFoundationService;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.BomFactsPort;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.RoutingFactsPort;
import com.ailearn.platform.core.manufacturing.foundation.domain.port.SalesFactsPort;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WorkOrder 生命周期应用服务。
 * <p>
 * 流程统一从可信上下文取得租户、用户和会话，再通过 foundation 端口读取 BOM、Routing 与销售来源；状态更新由
 * 生命周期聚合完成，库存和采购事实均不在此服务内直接写入。
 * </p>
 */
@Service
public class WorkOrderExecutionServiceImpl implements WorkOrderExecutionService {

    private final ManufacturingFoundationService foundationService;
    private final WorkOrderLifecycleRepository repository;
    private final BomFactsPort bomFactsPort;
    private final RoutingFactsPort routingFactsPort;
    private final SalesFactsPort salesFactsPort;
    private final FoundationIdempotencyExecutor idempotency;

    /** 使用内存幂等器构造 focused tests 所需的生命周期服务。 */
    public WorkOrderExecutionServiceImpl(ManufacturingFoundationService foundationService,
                                         WorkOrderLifecycleRepository repository,
                                         BomFactsPort bomFactsPort,
                                         RoutingFactsPort routingFactsPort,
                                         SalesFactsPort salesFactsPort) {
        this(foundationService, repository, bomFactsPort, routingFactsPort, salesFactsPort,
                new InMemoryIdempotencyStorage(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /** 创建可替换幂等存储和序列化器的生命周期服务。 */
    @Autowired
    public WorkOrderExecutionServiceImpl(ManufacturingFoundationService foundationService,
                                         WorkOrderLifecycleRepository repository,
                                         BomFactsPort bomFactsPort,
                                         RoutingFactsPort routingFactsPort,
                                         SalesFactsPort salesFactsPort,
                                         IdempotencyStorage storage,
                                         ObjectMapper objectMapper) {
        this.foundationService = foundationService;
        this.repository = repository;
        this.bomFactsPort = bomFactsPort;
        this.routingFactsPort = routingFactsPort;
        this.salesFactsPort = salesFactsPort;
        this.idempotency = new FoundationIdempotencyExecutor(storage, objectMapper);
    }

    /** 创建 foundation Draft 工单并登记必需工序；重复命令返回同一生命周期聚合。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:create')")
    public WorkOrderLifecycle createWorkOrder(WorkOrderCreateRequest request, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        return idempotency.execute("manufacturing:execution:work-order:create", tenantId, idempotencyKey,
                request, WorkOrderLifecycle.class, () -> {
                    WorkOrderFact workOrder = foundationService.createWorkOrder(request, idempotencyKey);
                    RoutingFact routing = activeRouting(tenantId, workOrder);
                    return repository.saveIfAbsent(WorkOrderLifecycle.initial(workOrder,
                            requiredOperationIds(routing)));
                });
    }

    /** 提交 Draft 或 Rejected 工单并保存本次提交审计。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:submit')")
    public WorkOrderLifecycle submit(UUID workOrderId, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        return mutate("submit", tenantId, workOrderId, idempotencyKey, workOrderId,
                current -> requireStatus(current, WorkOrderStatus.Draft, WorkOrderStatus.Rejected)
                        .submitted(userId, now()));
    }

    /** 审核工单并重新校验同租户有效 BOM、Routing 和销售来源后锁定版本。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:approve')")
    public WorkOrderLifecycle approve(UUID workOrderId, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        return mutate("approve", tenantId, workOrderId, idempotencyKey, workOrderId,
                current -> {
                    requireStatus(current, WorkOrderStatus.PendingApproval);
                    WorkOrderFact workOrder = current.workOrder();
                    BomFact bom = activeBom(tenantId, workOrder);
                    RoutingFact routing = activeRouting(tenantId, workOrder);
                    validateSalesSource(tenantId, workOrder);
                    if (!workOrder.bomVersion().equals(bom.version())
                            || !workOrder.routingVersion().equals(routing.version())) {
                        throw error(WorkOrderExecutionErrorCode.MES_WO_005,
                                "审核时 BOM/Routing 版本已变化，工单必须重新创建");
                    }
                    return current.approved(bom.version(), routing.version(), userId, now());
                });
    }

    /** 驳回待审核工单并强制保存非空拒绝原因。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:approve')")
    public WorkOrderLifecycle reject(UUID workOrderId, String reason, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        return mutate("reject", tenantId, workOrderId, idempotencyKey,
                new ReasonPayload(workOrderId, reason), current -> {
                    requireText(reason, "rejectionReason");
                    requireStatus(current, WorkOrderStatus.PendingApproval);
                    return current.rejected(reason.trim(), userId, now());
                });
    }

    /** 在派工执行层确认首次实际开工后推进工单到 InProgress。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public WorkOrderLifecycle startProduction(UUID workOrderId, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        return mutate("start-production", tenantId, workOrderId, idempotencyKey, workOrderId,
                current -> {
                    if (current.status() == WorkOrderStatus.InProgress) {
                        // 多道工序可以先后开工；首道工序推进到 InProgress，后续工序只需保持该状态。
                        return current;
                    }
                    if (current.status() != WorkOrderStatus.Released) {
                        throw error(WorkOrderExecutionErrorCode.MES_WO_002,
                                "只有已下达工单才能开始生产");
                    }
                    return current.inProgress();
                });
    }

    /** 接收后续执行链路进度并阻止累计报工超过计划数量。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:execution:manage')")
    public WorkOrderLifecycle recordProgress(UUID workOrderId, WorkOrderProgress progress,
                                             String idempotencyKey) {
        UUID tenantId = trustedTenant();
        return mutate("record-progress", tenantId, workOrderId, idempotencyKey,
                new ProgressPayload(workOrderId, progress), current -> {
                    requireStatus(current, WorkOrderStatus.InProgress);
                    if (!current.requiredOperationIds().containsAll(progress.completedOperationIds())) {
                        throw error(WorkOrderExecutionErrorCode.MES_WO_005,
                                "进度包含不属于当前 Routing 的工序");
                    }
                    if (progress.reportedQty().compareTo(current.workOrder().plannedQty()) > 0) {
                        throw error(WorkOrderExecutionErrorCode.MES_WO_003,
                                "累计报工数量超出工单计划数量");
                    }
                    return current.withProgress(progress);
                });
    }

    /** 只有完整执行事实满足数量、质检和入库条件时才允许正常完成。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:complete')")
    public WorkOrderLifecycle complete(UUID workOrderId, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        String sessionId = trustedSession();
        return mutate("complete", tenantId, workOrderId, idempotencyKey, workOrderId, current -> {
            requireStatus(current, WorkOrderStatus.InProgress);
            WorkOrderProgress progress = current.progress();
            if (!progress.completedOperationIds().containsAll(current.requiredOperationIds())
                    || progress.reportedQty().compareTo(current.workOrder().plannedQty()) != 0
                    || progress.receivedQty().compareTo(progress.qualifiedQty()) != 0
                    || progress.qualityBlocked() || progress.pendingInventoryCommands()) {
                throw error(WorkOrderExecutionErrorCode.MES_WO_001,
                        "必需工序、报工、质检、成品入库或在途库存约束尚未满足");
            }
            return current.completedNormally(userId, sessionId, now());
        });
    }

    /** 人工终止剩余生产，只保存原因和审计字段，不创建任何补造事实。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:complete')")
    public WorkOrderLifecycle manualComplete(UUID workOrderId, String reason, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        String sessionId = trustedSession();
        return mutate("manual-complete", tenantId, workOrderId, idempotencyKey,
                new ReasonPayload(workOrderId, reason), current -> {
                    requireText(reason, "completionReason");
                    requireStatus(current, WorkOrderStatus.Released, WorkOrderStatus.InProgress);
                    if (current.progress().pendingInventoryCommands()) {
                        throw error(WorkOrderExecutionErrorCode.MES_WO_001,
                                "存在未完成的在途库存命令，不能人工完成");
                    }
                    return current.completedManually(reason.trim(), userId, sessionId, now());
                });
    }

    /** 查询当前租户工单；租户不匹配时直接返回空。 */
    @Override
    @PreAuthorize("hasAuthority('mes:workorder:view')")
    public java.util.Optional<WorkOrderLifecycle> find(UUID workOrderId) {
        return repository.find(trustedTenant(), workOrderId);
    }

    /** 读取基础工单对应的有效 BOM。 */
    private BomFact activeBom(UUID tenantId, WorkOrderFact workOrder) {
        return bomFactsPort.findActiveBom(tenantId, workOrder.bomId())
                .filter(value -> value.isActiveFor(tenantId, workOrder.productId()))
                .orElseThrow(() -> error(WorkOrderExecutionErrorCode.MES_WO_005,
                        "BOM 不存在、已失效、跨租户或产品不一致"));
    }

    /** 读取基础工单对应的有效 Routing。 */
    private RoutingFact activeRouting(UUID tenantId, WorkOrderFact workOrder) {
        return routingFactsPort.findActiveRouting(tenantId, workOrder.routingId())
                .filter(value -> value.isActiveFor(tenantId, workOrder.productId()))
                .orElseThrow(() -> error(WorkOrderExecutionErrorCode.MES_WO_005,
                        "Routing 不存在、已失效、跨租户或产品不一致"));
    }

    /** 把 Routing 的工序标识冻结在工单生命周期中，避免后续路线变化影响完成判断。 */
    private Set<UUID> requiredOperationIds(RoutingFact routing) {
        return routing.operations().stream().map(operation -> operation.id()).collect(Collectors.toUnmodifiableSet());
    }

    /** 重新校验工单可选销售来源的租户和产品关系。 */
    private void validateSalesSource(UUID tenantId, WorkOrderFact workOrder) {
        if (workOrder.sourceSalesOrderLineId() == null) {
            return;
        }
        salesFactsPort.findActiveLine(tenantId, workOrder.sourceSalesOrderLineId())
                .filter(line -> line.productId().equals(workOrder.productId()))
                .orElseThrow(() -> error(WorkOrderExecutionErrorCode.MES_WO_004,
                        "来源销售订单行不存在、已失效、跨租户或产品不一致"));
    }

    /** 在指定租户工单上执行幂等状态变更，并把状态规则集中交给生命周期聚合。 */
    private WorkOrderLifecycle mutate(String operation, UUID tenantId, UUID workOrderId,
                                      String idempotencyKey, Object payload,
                                      UnaryOperator<WorkOrderLifecycle> updater) {
        return idempotency.execute("manufacturing:execution:work-order:" + operation, tenantId,
                idempotencyKey, payload, WorkOrderLifecycle.class,
                () -> {
                    WorkOrderLifecycle updated = repository.update(tenantId, workOrderId, updater);
                    if (updated == null) {
                        throw error(WorkOrderExecutionErrorCode.MES_TENANT_001,
                                "工单不存在或不属于当前租户");
                    }
                    return updated;
                });
    }

    /** 校验工单当前状态是否属于允许集合。 */
    private WorkOrderLifecycle requireStatus(WorkOrderLifecycle current, WorkOrderStatus... allowed) {
        for (WorkOrderStatus status : allowed) {
            if (current.status() == status) {
                return current;
            }
        }
        throw error(WorkOrderExecutionErrorCode.MES_WO_001,
                "当前状态 " + current.status() + " 不允许该操作");
    }

    /** 校验需要保存的人工原因。 */
    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw error(WorkOrderExecutionErrorCode.MES_WO_005, fieldName + " 不能为空");
        }
    }

    /** 从共享上下文读取可信租户。 */
    private UUID trustedTenant() {
        return TenantContextHolder.requireTenantId();
    }

    /** 从共享上下文读取可信用户。 */
    private UUID trustedUser() {
        return UserContextHolder.requireUserId();
    }

    /** 从共享上下文读取当前会话 JTI，人工完成必须带有该审计值。 */
    private String trustedSession() {
        String sessionId = UserContextHolder.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw error(WorkOrderExecutionErrorCode.MES_WO_005, "当前会话不能为空");
        }
        return sessionId;
    }

    /** 生成统一使用 UTC 的业务时间。 */
    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** 创建带业务码的受控异常。 */
    private WorkOrderExecutionException error(WorkOrderExecutionErrorCode code, String detail) {
        return new WorkOrderExecutionException(code, detail);
    }

    /** 幂等摘要所需的拒绝或人工完成载荷。 */
    private record ReasonPayload(UUID workOrderId, String reason) {
    }

    /** 幂等摘要所需的进度载荷。 */
    private record ProgressPayload(UUID workOrderId, WorkOrderProgress progress) {
    }
}
