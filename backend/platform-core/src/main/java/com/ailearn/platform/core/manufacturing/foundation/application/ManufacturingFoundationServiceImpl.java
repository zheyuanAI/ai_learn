package com.ailearn.platform.core.manufacturing.foundation.application;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomComponentFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingOperationFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.SalesLineFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomComponentRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingOperationRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.exception.FoundationErrorCode;
import com.ailearn.platform.core.manufacturing.foundation.exception.FoundationException;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MES foundation 应用服务。
 * <p>
 * 所有租户和操作人均来自可信上下文；工单创建只保存生产意图和软引用，不改写销售事实、不预留库存，
 * 也不触发 MRP、自动排产或采购。BOM/Routing 的 ACTIVE 版本号在创建工单时复制，供后续 S5 审核下达锁定。
 * </p>
 */
@Service
public class ManufacturingFoundationServiceImpl implements ManufacturingFoundationService {

    private final FoundationRepository repository;
    private final FoundationIdempotencyExecutor idempotency;

    /**
     * 使用默认内存幂等存储创建 foundation 服务，便于当前无迁移阶段运行 focused tests。
     *
     * @param repository foundation 事实适配器
     */
    public ManufacturingFoundationServiceImpl(FoundationRepository repository) {
        this(repository, new InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建可替换幂等存储和序列化器的 foundation 服务。
     *
     * @param repository foundation 事实适配器
     * @param storage 幂等记录存储
     * @param objectMapper 结果序列化器
     */
    @Autowired
    public ManufacturingFoundationServiceImpl(FoundationRepository repository,
                                               IdempotencyStorage storage,
                                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.idempotency = new FoundationIdempotencyExecutor(storage, objectMapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:bom:manage')")
    public BomFact createBom(BomCreateRequest request, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        return idempotency.execute("manufacturing:foundation:bom:create", tenantId, idempotencyKey,
                request, BomFact.class,
                () -> buildBom(request, tenantId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:routing:manage')")
    public RoutingFact createRouting(RoutingCreateRequest request, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        return idempotency.execute("manufacturing:foundation:routing:create", tenantId, idempotencyKey,
                request, RoutingFact.class,
                () -> buildRouting(request, tenantId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('mes:workorder:create')")
    public WorkOrderFact createWorkOrder(WorkOrderCreateRequest request, String idempotencyKey) {
        UUID tenantId = trustedTenant();
        UUID userId = trustedUser();
        return idempotency.execute("manufacturing:foundation:work-order:create", tenantId, idempotencyKey,
                request, WorkOrderFact.class,
                () -> buildWorkOrder(request, tenantId, userId));
    }

    private BomFact buildBom(BomCreateRequest request, UUID tenantId, UUID userId) {
        if (request == null) {
            throw invalid("BOM 请求不能为空");
        }
        try {
            List<BomComponentFact> components = request.components() == null ? List.of()
                    : request.components().stream().map(this::toComponent).toList();
            return repository.saveBom(new BomFact(UUID.randomUUID(), tenantId, request.productId(),
                    request.bomCode(), request.version(), request.status() == null ? BomStatus.DRAFT : request.status(),
                    components, false, userId, OffsetDateTime.now(ZoneOffset.UTC)));
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private RoutingFact buildRouting(RoutingCreateRequest request, UUID tenantId, UUID userId) {
        if (request == null) {
            throw invalid("Routing 请求不能为空");
        }
        try {
            List<RoutingOperationFact> operations = request.operations() == null ? List.of()
                    : request.operations().stream().map(this::toOperation).toList();
            return repository.saveRouting(new RoutingFact(UUID.randomUUID(), tenantId, request.productId(),
                    request.routingCode(), request.version(),
                    request.status() == null ? RoutingStatus.DRAFT : request.status(),
                    operations, false, userId, OffsetDateTime.now(ZoneOffset.UTC)));
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private WorkOrderFact buildWorkOrder(WorkOrderCreateRequest request, UUID tenantId, UUID userId) {
        if (request == null) {
            throw workOrderInvalid("WorkOrder 请求不能为空");
        }
        requireWorkOrderFields(request);
        BomFact bom = repository.findActiveBom(tenantId, request.bomId())
                .orElseThrow(() -> workOrderInvalid("BOM 不存在、已失效或不属于当前租户"));
        if (!bom.isActiveFor(tenantId, request.productId())) {
            throw workOrderInvalid("BOM 与工单产品不一致");
        }
        RoutingFact routing = repository.findActiveRouting(tenantId, request.routingId())
                .orElseThrow(() -> workOrderInvalid("Routing 不存在、已失效或不属于当前租户"));
        if (!routing.isActiveFor(tenantId, request.productId())) {
            throw workOrderInvalid("Routing 与工单产品不一致");
        }
        if (request.sourceSalesOrderLineId() != null) {
            SalesLineFact salesLine = repository.findActiveLine(tenantId, request.sourceSalesOrderLineId())
                    .orElseThrow(() -> new FoundationException(FoundationErrorCode.MES_WO_004,
                            "来源销售订单行不存在、已失效或不属于当前租户"));
            if (!salesLine.tenantId().equals(tenantId)) {
                throw new FoundationException(FoundationErrorCode.MES_TENANT_001,
                        "来源销售订单行不属于当前租户");
            }
            if (!salesLine.productId().equals(request.productId())) {
                throw new FoundationException(FoundationErrorCode.MES_WO_004,
                        "来源销售订单行产品与工单产品不一致");
            }
        }
        String workOrderNo = request.workOrderNo() == null || request.workOrderNo().isBlank()
                ? "WO-" + UUID.randomUUID() : request.workOrderNo().trim();
        try {
            return repository.saveWorkOrder(new WorkOrderFact(UUID.randomUUID(), tenantId, workOrderNo,
                    request.productId(), request.plannedQty(), request.plannedStartTime(),
                    request.plannedFinishTime(), bom.id(), bom.version(), routing.id(), routing.version(),
                    request.sourceSalesOrderLineId(), WorkOrderStatus.Draft, false, userId,
                    OffsetDateTime.now(ZoneOffset.UTC)));
        } catch (IllegalArgumentException exception) {
            throw workOrderInvalid(exception.getMessage());
        }
    }

    private BomComponentFact toComponent(BomComponentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BOM 组件不能为空");
        }
        return new BomComponentFact(request.componentProductId(), request.componentQty(), request.uom(),
                request.scrapRate());
    }

    private RoutingOperationFact toOperation(RoutingOperationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Routing 工序不能为空");
        }
        return new RoutingOperationFact(UUID.randomUUID(), request.operationNo(), request.operationName(),
                request.workCenterId(), request.standardTimeMinutes());
    }

    private void requireWorkOrderFields(WorkOrderCreateRequest request) {
        if (request.productId() == null || request.bomId() == null || request.routingId() == null) {
            throw workOrderInvalid("productId、bomId、routingId 不能为空");
        }
        if (request.plannedQty() == null || request.plannedQty().signum() <= 0) {
            throw workOrderInvalid("plannedQty 必须大于 0");
        }
        if (request.plannedStartTime() == null || request.plannedFinishTime() == null
                || !request.plannedFinishTime().isAfter(request.plannedStartTime())) {
            throw workOrderInvalid("计划完成时间必须晚于计划开始时间");
        }
    }

    private UUID trustedTenant() {
        return TenantContextHolder.requireTenantId();
    }

    private UUID trustedUser() {
        return UserContextHolder.requireUserId();
    }

    private FoundationException invalid(String message) {
        return new FoundationException(FoundationErrorCode.MES_FOUNDATION_001, message);
    }

    private FoundationException workOrderInvalid(String message) {
        return new FoundationException(FoundationErrorCode.MES_WO_005, message);
    }
}
