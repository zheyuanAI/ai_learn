package com.ailearn.platform.core.sales.fulfillment.application;

import com.ailearn.platform.core.config.CoreIdempotencyExecutor;
import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.application.InventoryCommandService;
import com.ailearn.platform.core.inventory.application.InventoryDecreaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryMutationResult;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.InventoryReleaseCommand;
import com.ailearn.platform.core.inventory.application.InventoryReservationQuery;
import com.ailearn.platform.core.inventory.application.InventoryReservationView;
import com.ailearn.platform.core.inventory.application.InventoryReserveCommand;
import com.ailearn.platform.core.inventory.application.InventoryMoveCommand;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.domain.LocationType;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.sales.domain.SalesFulfillmentFact;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.sales.dto.PickLineRequest;
import com.ailearn.platform.core.sales.dto.PickTaskConfirmRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnLineRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnRequest;
import com.ailearn.platform.core.sales.dto.ReservationReleaseLineRequest;
import com.ailearn.platform.core.sales.dto.ReservationReleaseRequest;
import com.ailearn.platform.core.sales.dto.SalesFulfillmentResult;
import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import com.ailearn.platform.core.sales.dto.ShipmentConfirmRequest;
import com.ailearn.platform.core.sales.dto.ShipmentLineRequest;
import com.ailearn.platform.core.sales.exception.SalesOrderErrorCode;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 销售履约应用服务。
 * <p>
 * 入参：销售履约请求、路径操作标识和幂等键；出参：订单履约摘要及库存事实标识；流程：可信上下文、
 * 订单版本和数量校验 -> 查询/补足业务预留 -> 仅通过 InventoryCommandService 移动或扣减 ->
 * 订单行与履约事实同事务保存。服务不直接注入库存 Mapper 或 Repository。
 * </p>
 */
@Service
public class SalesFulfillmentApplicationServiceImpl implements SalesFulfillmentApplicationService {

    private static final String SALES_SOURCE = "SALES_ORDER";
    private static final int QUERY_SIZE = 200;

    private final SalesOrderRepository repository;
    private final InventoryCommandService inventoryCommandService;
    private final InventoryQueryService inventoryQueryService;
    private final InventoryLocationPort locationPort;
    private final CoreIdempotencyExecutor idempotencyExecutor;

    /**
     * 创建供纯单元测试使用的履约服务。
     */
    public SalesFulfillmentApplicationServiceImpl(SalesOrderRepository repository,
                                                  InventoryCommandService inventoryCommandService,
                                                  InventoryQueryService inventoryQueryService,
                                                  InventoryLocationPort locationPort) {
        this(repository, inventoryCommandService, inventoryQueryService, locationPort,
                new com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建生产履约服务并接入共享幂等存储。
     * 入参：销售订单、库存命令/查询、库位端口、幂等存储和序列化器；出参：可执行销售履约命令的服务；
     * 流程：保存依赖，实际命令由各方法在事务内完成。
     */
    @Autowired
    public SalesFulfillmentApplicationServiceImpl(SalesOrderRepository repository,
                                                  InventoryCommandService inventoryCommandService,
                                                  InventoryQueryService inventoryQueryService,
                                                  InventoryLocationPort locationPort,
                                                  IdempotencyStorage storage,
                                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.inventoryCommandService = inventoryCommandService;
        this.inventoryQueryService = inventoryQueryService;
        this.locationPort = locationPort;
        this.idempotencyExecutor = new CoreIdempotencyExecutor(storage, objectMapper);
    }

    /**
     * 确认直接拣货：复用来源库位的未拣分配，不足部分只在本次来源库位自动预留。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:pick:confirm')")
    public SalesFulfillmentResult confirmPick(UUID pickTaskId, PickTaskConfirmRequest request, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        validatePickRequest(pickTaskId, request);
        return idempotencyExecutor.execute("sales:pick:confirm", actor.tenantId(), idempotencyKey,
                digest("pick", List.of(pickTaskId, request)), SalesFulfillmentResult.class, () -> {
                    SalesOrder order = approvedOrder(request.getSalesOrderId(), actor.tenantId());
                    Map<UUID, PickLineRequest> requested = uniquePickLines(request.getLines());
                    List<SalesOrderLine> updatedLines = new ArrayList<>(order.lines());
                    List<SalesFulfillmentFact> facts = new ArrayList<>();
                    List<UUID> transactionIds = new ArrayList<>();
                    List<UUID> reservationIds = new ArrayList<>();
                    for (PickLineRequest lineRequest : request.getLines()) {
                        SalesOrderLine line = line(order, lineRequest.getSalesOrderLineId());
                        BigDecimal quantity = positive(lineRequest.getPickedQty(), "pickedQty");
                        if (quantity.compareTo(line.unshippedQty()) > 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "拣货数量超过订单行未发货数量");
                        }
                        LocationSnapshot source = activeLocation(actor.tenantId(), lineRequest.getSourceLocationId());
                        LocationSnapshot shipping = activeLocation(actor.tenantId(), lineRequest.getShippingLocationId());
                        requirePickLocations(source, shipping);
                        InventoryDimension shippingDimension = dimension(line, shipping, "");
                        List<ReservationPart> sourceParts = partsAt(order, line, source.id(), actor.tenantId());
                        BigDecimal existingAtSource = sum(sourceParts);
                        if (line.unpickedQty().compareTo(existingAtSource) > 0
                                && quantity.compareTo(line.unpickedQty()) <= 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "订单行已有未拣预留不在请求来源库位");
                        }
                        BigDecimal reserveQty = quantity.subtract(existingAtSource).max(BigDecimal.ZERO);
                        if (reserveQty.compareTo(line.unreservedQty()) > 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "自动预留数量超过订单行未预留数量");
                        }
                        if (reserveQty.signum() > 0) {
                            InventoryMutationResult reserved = inventoryCommandService.reserve(
                                    reserveCommand(actor, idempotencyKey, pickTaskId, line, source, reserveQty));
                            collectTransactions(reserved, transactionIds);
                            InventoryReservation reservation = requiredReservation(reserved);
                            reservationIds.add(reservation.id());
                            InventoryReservationAllocation allocation = requiredAllocation(reserved);
                            sourceParts = new ArrayList<>(sourceParts);
                            sourceParts.add(new ReservationPart(reservation, allocation));
                        }
                        BigDecimal remaining = quantity;
                        int index = 0;
                        for (ReservationPart part : sourceParts) {
                            if (remaining.signum() == 0) {
                                break;
                            }
                            BigDecimal moved = remaining.min(part.allocation().activeQty());
                            if (moved.signum() <= 0) {
                                continue;
                            }
                            InventoryMutationResult movedResult = inventoryCommandService.move(new InventoryMoveCommand(
                                    moveMetadata(actor, idempotencyKey, pickTaskId, line, "PICK", index++),
                                    part.allocation().dimension(), shippingDimension, moved,
                                    part.reservation().id(), part.allocation().id()));
                            collectTransactions(movedResult, transactionIds);
                            facts.add(fact(actor, "PICK", order.id(), pickTaskId, line.id(), moved,
                                    part.allocation().dimension().locationId(), shipping.id(),
                                    part.reservation().id(), part.allocation().id(), idempotencyKey));
                            reservationIds.add(part.reservation().id());
                            remaining = remaining.subtract(moved);
                        }
                        if (remaining.signum() != 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "来源库位有效预留分配不足");
                        }
                        replaceLine(updatedLines, line.withFulfillment(line.reservedQty().add(reserveQty),
                                line.pickedQty().add(quantity), line.shippedQty()));
                    }
                    SalesOrder updated = order.fulfillmentUpdated(updatedLines, actor.userId(), now());
                    SalesOrder saved = repository.updateFulfillment(updated, order.version(), facts);
                    return result("PICK", pickTaskId, saved, transactionIds, reservationIds);
                });
    }

    /**
     * 将未发货暂存数量连同有效预留分配退回指定合法来源库位。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:pick:return')")
    public SalesFulfillmentResult returnPick(UUID pickTaskId, PickTaskReturnRequest request, String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        validateReturnRequest(pickTaskId, request);
        return idempotencyExecutor.execute("sales:pick:return", actor.tenantId(), idempotencyKey,
                digest("pick-return", List.of(pickTaskId, request)), SalesFulfillmentResult.class, () -> {
                    SalesOrder order = approvedOrder(request.getSalesOrderId(), actor.tenantId());
                    List<SalesOrderLine> updatedLines = new ArrayList<>(order.lines());
                    List<SalesFulfillmentFact> facts = new ArrayList<>();
                    List<UUID> transactionIds = new ArrayList<>();
                    List<UUID> reservationIds = new ArrayList<>();
                    Set<UUID> seen = new HashSet<>();
                    for (PickTaskReturnLineRequest lineRequest : request.getLines()) {
                        if (!seen.add(lineRequest.getSalesOrderLineId())) {
                            throw new ValidationException("退回明细不能重复");
                        }
                        SalesOrderLine line = line(order, lineRequest.getSalesOrderLineId());
                        BigDecimal quantity = positive(lineRequest.getReturnQty(), "returnQty");
                        if (quantity.compareTo(line.shippingStagedQty()) > 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "退回数量超过未发货暂存数量");
                        }
                        LocationSnapshot target = activeLocation(actor.tenantId(), lineRequest.getToLocationId());
                        requireReturnTarget(target);
                        List<ReservationPart> staged = partsByType(order, line, LocationType.ShippingStaging,
                                actor.tenantId());
                        BigDecimal remaining = quantity;
                        int index = 0;
                        for (ReservationPart part : staged) {
                            if (remaining.signum() == 0) {
                                break;
                            }
                            BigDecimal moved = remaining.min(part.allocation().activeQty());
                            if (moved.signum() <= 0) {
                                continue;
                            }
                            InventoryDimension targetDimension = dimension(line, target,
                                    part.allocation().dimension().normalizedLotNo());
                            InventoryMutationResult movedResult = inventoryCommandService.move(new InventoryMoveCommand(
                                    moveMetadata(actor, idempotencyKey, pickTaskId, line, "PICK_RETURN", index++),
                                    part.allocation().dimension(), targetDimension, moved,
                                    part.reservation().id(), part.allocation().id()));
                            collectTransactions(movedResult, transactionIds);
                            facts.add(fact(actor, "PICK_RETURN", order.id(), pickTaskId, line.id(), moved,
                                    part.allocation().dimension().locationId(), target.id(),
                                    part.reservation().id(), part.allocation().id(), idempotencyKey));
                            reservationIds.add(part.reservation().id());
                            remaining = remaining.subtract(moved);
                        }
                        if (remaining.signum() != 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "发货暂存位有效预留分配不足");
                        }
                        replaceLine(updatedLines, line.withFulfillment(line.reservedQty(),
                                line.pickedQty().subtract(quantity), line.shippedQty()));
                    }
                    SalesOrder updated = order.fulfillmentUpdated(updatedLines, actor.userId(), now());
                    SalesOrder saved = repository.updateFulfillment(updated, order.version(), facts);
                    return result("PICK_RETURN", pickTaskId, saved, transactionIds, reservationIds);
                });
    }

    /**
     * 仅释放订单行尚未拣货的有效预留；已在发货暂存位的数量必须先退回。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:reservation:release')")
    public SalesFulfillmentResult releaseReservations(UUID salesOrderId, ReservationReleaseRequest request,
                                                      String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        if (salesOrderId == null || request == null || request.getReleaseLines() == null
                || request.getReleaseLines().isEmpty()) {
            throw new ValidationException("释放预留必须提供明细");
        }
        return idempotencyExecutor.execute("sales:reservation:release", actor.tenantId(), idempotencyKey,
                digest("reservation-release", List.of(salesOrderId, request)), SalesFulfillmentResult.class, () -> {
                    SalesOrder order = approvedOrder(salesOrderId, actor.tenantId());
                    List<SalesOrderLine> updatedLines = new ArrayList<>(order.lines());
                    List<SalesFulfillmentFact> facts = new ArrayList<>();
                    List<UUID> transactionIds = new ArrayList<>();
                    List<UUID> reservationIds = new ArrayList<>();
                    Set<UUID> seen = new HashSet<>();
                    for (ReservationReleaseLineRequest lineRequest : request.getReleaseLines()) {
                        if (lineRequest == null || !seen.add(lineRequest.getSalesOrderLineId())) {
                            throw new ValidationException("释放明细不能为空且不能重复");
                        }
                        if (lineRequest.getReason() == null || lineRequest.getReason().trim().isBlank()) {
                            throw new ValidationException("释放预留必须填写原因");
                        }
                        SalesOrderLine line = line(order, lineRequest.getSalesOrderLineId());
                        BigDecimal quantity = positive(lineRequest.getReleaseQty(), "releaseQty");
                        if (quantity.compareTo(line.unpickedQty()) > 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_003,
                                    "释放数量超过未拣预留，已拣数量必须先退回");
                        }
                        List<ReservationPart> parts = partsNotAtType(order, line, LocationType.ShippingStaging,
                                actor.tenantId());
                        BigDecimal remaining = quantity;
                        int index = 0;
                        for (ReservationPart part : parts) {
                            if (remaining.signum() == 0) {
                                break;
                            }
                            BigDecimal released = remaining.min(part.allocation().activeQty());
                            if (released.signum() <= 0) {
                                continue;
                            }
                            InventoryMutationResult releasedResult = inventoryCommandService.release(
                                    releaseCommand(actor, idempotencyKey, salesOrderId, line,
                                            part, released, index++));
                            collectTransactions(releasedResult, transactionIds);
                            facts.add(fact(actor, "RESERVATION_RELEASE", order.id(), salesOrderId, line.id(), released,
                                    part.allocation().dimension().locationId(), null,
                                    part.reservation().id(), part.allocation().id(), idempotencyKey));
                            reservationIds.add(part.reservation().id());
                            remaining = remaining.subtract(released);
                        }
                        if (remaining.signum() != 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "订单行有效预留分配不足");
                        }
                        replaceLine(updatedLines, line.withFulfillment(line.reservedQty().subtract(quantity),
                                line.pickedQty(), line.shippedQty()));
                    }
                    SalesOrder updated = order.fulfillmentUpdated(updatedLines, actor.userId(), now());
                    SalesOrder saved = repository.updateFulfillment(updated, order.version(), facts);
                    return result("RESERVATION_RELEASE", salesOrderId, saved, transactionIds, reservationIds);
                });
    }

    /**
     * 从发货暂存位先释放对应有效预留，再扣减实物并累计发货数量。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:shipment:confirm')")
    public SalesFulfillmentResult confirmShipment(UUID shipmentId, ShipmentConfirmRequest request,
                                                   String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        if (shipmentId == null || request == null || request.getSalesOrderId() == null
                || request.getShipmentLines() == null || request.getShipmentLines().isEmpty()) {
            throw new ValidationException("发货必须提供销售订单和明细");
        }
        OffsetDateTime shipTime = request.getShipTime() == null ? now() : request.getShipTime();
        return idempotencyExecutor.execute("sales:shipment:confirm", actor.tenantId(), idempotencyKey,
                digest("shipment", List.of(shipmentId, request)), SalesFulfillmentResult.class, () -> {
                    SalesOrder order = approvedOrder(request.getSalesOrderId(), actor.tenantId());
                    List<SalesOrderLine> updatedLines = new ArrayList<>(order.lines());
                    List<SalesFulfillmentFact> facts = new ArrayList<>();
                    List<UUID> transactionIds = new ArrayList<>();
                    List<UUID> reservationIds = new ArrayList<>();
                    Set<UUID> seen = new HashSet<>();
                    for (ShipmentLineRequest lineRequest : request.getShipmentLines()) {
                        if (lineRequest == null || !seen.add(lineRequest.getSalesOrderLineId())) {
                            throw new ValidationException("发货明细不能为空且不能重复");
                        }
                        SalesOrderLine line = line(order, lineRequest.getSalesOrderLineId());
                        if (lineRequest.getProductId() == null || !line.productId().equals(lineRequest.getProductId())) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "发货产品与销售订单行不一致");
                        }
                        BigDecimal quantity = positive(lineRequest.getShipQty(), "shipQty");
                        if (quantity.compareTo(line.shippingStagedQty()) > 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "发货数量超过发货暂存数量");
                        }
                        List<ReservationPart> staged = partsByType(order, line, LocationType.ShippingStaging,
                                actor.tenantId());
                        BigDecimal remaining = quantity;
                        int index = 0;
                        for (ReservationPart part : staged) {
                            if (remaining.signum() == 0) {
                                break;
                            }
                            BigDecimal shipped = remaining.min(part.allocation().activeQty());
                            if (shipped.signum() <= 0) {
                                continue;
                            }
                            InventoryDimension dimension = part.allocation().dimension();
                            InventoryMutationResult released = inventoryCommandService.release(
                                    releaseCommand(actor, idempotencyKey, shipmentId, line, part, shipped, index++));
                            collectTransactions(released, transactionIds);
                            InventoryMutationResult decreased = inventoryCommandService.decrease(
                                    decreaseCommand(actor, idempotencyKey, shipmentId, line, dimension, shipped,
                                            shipTime, index++));
                            collectTransactions(decreased, transactionIds);
                            facts.add(fact(actor, "SHIP", order.id(), shipmentId, line.id(), shipped,
                                    dimension.locationId(), null, part.reservation().id(), part.allocation().id(),
                                    idempotencyKey, shipTime));
                            reservationIds.add(part.reservation().id());
                            remaining = remaining.subtract(shipped);
                        }
                        if (remaining.signum() != 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "发货暂存位有效库存或预留不足");
                        }
                        replaceLine(updatedLines, line.withFulfillment(line.reservedQty(), line.pickedQty(),
                                line.shippedQty().add(quantity)));
                    }
                    SalesOrder updated = order.fulfillmentUpdated(updatedLines, actor.userId(), shipTime);
                    SalesOrder saved = repository.updateFulfillment(updated, order.version(), facts);
                    return result("SHIP", shipmentId, saved, transactionIds, reservationIds);
                });
    }

    /**
     * 人工完成前释放所有未拣预留；不补造拣货、发货或库存事实。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('sales:order:complete')")
    public SalesFulfillmentResult manuallyComplete(UUID salesOrderId, SalesOrderCompleteRequest request,
                                                   String idempotencyKey) {
        Actor actor = actor();
        requireKey(idempotencyKey);
        if (salesOrderId == null || request == null || request.getCompletionReason() == null
                || request.getCompletionReason().trim().isBlank()) {
            throw new ValidationException("人工完成原因不能为空");
        }
        return idempotencyExecutor.execute("sales:order:complete", actor.tenantId(), idempotencyKey,
                digest("manual-complete", List.of(salesOrderId, request.getCompletionReason().trim())),
                SalesFulfillmentResult.class, () -> {
                    SalesOrder order = approvedOrder(salesOrderId, actor.tenantId());
                    List<SalesOrderLine> updatedLines = new ArrayList<>(order.lines());
                    List<SalesFulfillmentFact> facts = new ArrayList<>();
                    List<UUID> transactionIds = new ArrayList<>();
                    List<UUID> reservationIds = new ArrayList<>();
                    for (SalesOrderLine line : order.lines()) {
                        BigDecimal quantity = line.unpickedQty();
                        if (quantity.signum() == 0) {
                            continue;
                        }
                        List<ReservationPart> parts = partsNotAtType(order, line, LocationType.ShippingStaging,
                                actor.tenantId());
                        BigDecimal remaining = quantity;
                        int index = 0;
                        for (ReservationPart part : parts) {
                            if (remaining.signum() == 0) {
                                break;
                            }
                            BigDecimal released = remaining.min(part.allocation().activeQty());
                            if (released.signum() <= 0) {
                                continue;
                            }
                            InventoryMutationResult releasedResult = inventoryCommandService.release(
                                    releaseCommand(actor, idempotencyKey, salesOrderId, line, part,
                                            released, index++));
                            collectTransactions(releasedResult, transactionIds);
                            facts.add(fact(actor, "RESERVATION_RELEASE", order.id(), salesOrderId, line.id(), released,
                                    part.allocation().dimension().locationId(), null,
                                    part.reservation().id(), part.allocation().id(), idempotencyKey));
                            reservationIds.add(part.reservation().id());
                            remaining = remaining.subtract(released);
                        }
                        if (remaining.signum() != 0) {
                            throw new SalesOrderException(SalesOrderErrorCode.SO_002,
                                    "人工完成时订单行未拣预留分配不足");
                        }
                        replaceLine(updatedLines, line.withFulfillment(line.pickedQty(), line.pickedQty(),
                                line.shippedQty()));
                    }
                    SalesOrder completed = order.manuallyCompleteAfterRelease(updatedLines,
                            request.getCompletionReason().trim(), actor.userId(), actor.sessionId(), now());
                    SalesOrder saved = repository.updateFulfillment(completed, order.version(), facts);
                    return result("MANUAL_COMPLETE", salesOrderId, saved, transactionIds, reservationIds);
                });
    }

    private SalesOrder approvedOrder(UUID id, UUID tenantId) {
        if (id == null) {
            throw new NotFoundException("销售订单不存在");
        }
        SalesOrder order = repository.findById(tenantId, id)
                .orElseThrow(() -> new NotFoundException("销售订单不存在"));
        if (order.status() != SalesOrderStatus.Approved) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_001, "只有 Approved 订单允许履约操作");
        }
        return order;
    }

    private SalesOrderLine line(SalesOrder order, UUID lineId) {
        if (lineId == null) {
            throw new ValidationException("salesOrderLineId 不能为空");
        }
        return order.lines().stream().filter(line -> line.id().equals(lineId)).findFirst()
                .orElseThrow(() -> new SalesOrderException(SalesOrderErrorCode.SO_002,
                        "销售订单明细不存在或不属于当前订单"));
    }

    private Map<UUID, PickLineRequest> uniquePickLines(List<PickLineRequest> lines) {
        Map<UUID, PickLineRequest> result = new LinkedHashMap<>();
        for (PickLineRequest line : lines) {
            if (line == null || line.getSalesOrderLineId() == null || result.put(line.getSalesOrderLineId(), line) != null) {
                throw new ValidationException("拣货明细不能为空且不能重复");
            }
        }
        return result;
    }

    private List<ReservationPart> partsAt(SalesOrder order, SalesOrderLine line, UUID locationId, UUID tenantId) {
        return reservations(order, line, tenantId).stream()
                .flatMap(reservation -> reservation.allocations().stream()
                        .filter(allocation -> allocation.activeQty().signum() > 0
                                && allocation.dimension().productId().equals(line.productId())
                                && allocation.dimension().locationId().equals(locationId))
                        .map(allocation -> new ReservationPart(reservation.reservation(), allocation)))
                .toList();
    }

    private List<ReservationPart> partsByType(SalesOrder order, SalesOrderLine line, LocationType type,
                                              UUID tenantId) {
        return reservations(order, line, tenantId).stream()
                .flatMap(reservation -> reservation.allocations().stream()
                        .filter(allocation -> allocation.activeQty().signum() > 0
                                && allocation.dimension().productId().equals(line.productId())
                                && locationType(tenantId, allocation.dimension().locationId()) == type)
                        .map(allocation -> new ReservationPart(reservation.reservation(), allocation)))
                .toList();
    }

    private List<ReservationPart> partsNotAtType(SalesOrder order, SalesOrderLine line, LocationType excluded,
                                                 UUID tenantId) {
        return reservations(order, line, tenantId).stream()
                .flatMap(reservation -> reservation.allocations().stream()
                        .filter(allocation -> allocation.activeQty().signum() > 0
                                && allocation.dimension().productId().equals(line.productId())
                                && locationType(tenantId, allocation.dimension().locationId()) != excluded)
                        .map(allocation -> new ReservationPart(reservation.reservation(), allocation)))
                .toList();
    }

    private List<InventoryReservationView> reservations(SalesOrder order, SalesOrderLine line, UUID tenantId) {
        List<InventoryReservationView> records = new ArrayList<>();
        int page = 1;
        while (true) {
            InventoryReservationPageResult result = reservationPage(new InventoryReservationQuery(tenantId, null,
                    SALES_SOURCE, order.id(), line.id(), null, line.productId(), null, null, null, page, QUERY_SIZE));
            records.addAll(result.content());
            if (!result.hasNext()) {
                break;
            }
            page++;
        }
        return List.copyOf(records);
    }

    private InventoryReservationPageResult reservationPage(InventoryReservationQuery query) {
        com.ailearn.platform.core.inventory.application.InventoryReservationPage page =
                inventoryQueryService.queryReservations(query);
        if (page == null || page.content() == null) {
            throw new ServiceUnavailableException("销售订单预留查询不可用");
        }
        return new InventoryReservationPageResult(page.content(), page.total(), page.page(), page.size());
    }

    private LocationSnapshot activeLocation(UUID tenantId, UUID locationId) {
        if (locationId == null) {
            throw new ValidationException("库位不能为空");
        }
        LocationSnapshot snapshot = locationPort.findByTenantIdAndId(tenantId, locationId);
        if (snapshot == null || !tenantId.equals(snapshot.tenantId()) || !locationId.equals(snapshot.id())
                || !snapshot.isActive() || snapshot.type() == null || snapshot.warehouseId() == null) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, "库位不存在、跨租户、未启用或仓库信息缺失");
        }
        return snapshot;
    }

    private LocationType locationType(UUID tenantId, UUID locationId) {
        return activeLocation(tenantId, locationId).type();
    }

    private InventoryDimension dimension(SalesOrderLine line, LocationSnapshot location, String lotNo) {
        return new InventoryDimension(line.productId(), location.warehouseId(), location.id(), lotNo);
    }

    private InventoryReserveCommand reserveCommand(Actor actor, String parentKey, UUID operationId,
                                                   SalesOrderLine line, LocationSnapshot source,
                                                   BigDecimal quantity) {
        return new InventoryReserveCommand(metadata(actor, childKey(parentKey, "reserve|" + line.id()),
                line, "RESERVE", operationId, now()), dimension(line, source, ""), quantity,
                "SO-" + operationId + "-" + line.id());
    }

    private InventoryReleaseCommand releaseCommand(Actor actor, String parentKey, UUID operationId,
                                                   SalesOrderLine line, ReservationPart part,
                                                   BigDecimal quantity, int index) {
        return new InventoryReleaseCommand(metadata(actor, childKey(parentKey,
                        "release|" + operationId + "|" + line.id() + "|" + index), line,
                "RELEASE", operationId, now()), part.reservation().id(), part.allocation().dimension(), quantity,
                part.allocation().id());
    }

    private InventoryDecreaseCommand decreaseCommand(Actor actor, String parentKey, UUID operationId,
                                                     SalesOrderLine line, InventoryDimension dimension,
                                                     BigDecimal quantity, OffsetDateTime businessTime, int index) {
        return new InventoryDecreaseCommand(metadata(actor, childKey(parentKey,
                        "decrease|" + operationId + "|" + line.id() + "|" + index), line,
                "SHIPMENT", operationId, businessTime), dimension, quantity);
    }

    private InventoryCommandMetadata moveMetadata(Actor actor, String parentKey, UUID operationId,
                                                  SalesOrderLine line, String transactionType, int index) {
        return metadata(actor, childKey(parentKey, transactionType + "|" + operationId + "|" + line.id() + "|" + index),
                line, transactionType, operationId, now());
    }

    private InventoryCommandMetadata metadata(Actor actor, String key, SalesOrderLine line,
                                              String transactionType, UUID sourceId,
                                              OffsetDateTime businessTime) {
        return new InventoryCommandMetadata(actor.tenantId(), actor.userId(), actor.sessionId(), actor.requestId(),
                key, "delegated", SALES_SOURCE, sourceId, line.id(), transactionType, businessTime);
    }

    private SalesFulfillmentFact fact(Actor actor, String action, UUID salesOrderId, UUID operationId, UUID lineId,
                                      BigDecimal quantity, UUID fromLocationId, UUID toLocationId,
                                      UUID reservationId, UUID allocationId, String idempotencyKey) {
        return fact(actor, action, salesOrderId, operationId, lineId, quantity, fromLocationId, toLocationId,
                reservationId, allocationId, idempotencyKey, now());
    }

    private SalesFulfillmentFact fact(Actor actor, String action, UUID salesOrderId, UUID operationId, UUID lineId,
                                      BigDecimal quantity, UUID fromLocationId, UUID toLocationId,
                                      UUID reservationId, UUID allocationId, String idempotencyKey,
                                      OffsetDateTime occurredAt) {
        return new SalesFulfillmentFact(UUID.randomUUID(), actor.tenantId(),
                salesOrderId, lineId, action, operationId, quantity, fromLocationId, toLocationId,
                reservationId, allocationId, idempotencyKey, actor.userId(), actor.sessionId(),
                actor.requestId(), occurredAt);
    }

    private SalesFulfillmentResult result(String action, UUID operationId, SalesOrder saved,
                                          List<UUID> transactionIds, List<UUID> reservationIds) {
        if (saved == null) {
            throw new ServiceUnavailableException("销售履约持久化结果为空");
        }
        return new SalesFulfillmentResult(action, operationId, new SalesOrderView(saved, actions(saved)),
                new ArrayList<>(new LinkedHashSet<>(transactionIds)),
                new ArrayList<>(new LinkedHashSet<>(reservationIds)));
    }

    private List<AllowedActionVo> actions(SalesOrder order) {
        if (order.status() == SalesOrderStatus.Completed) {
            return List.of();
        }
        return List.of(new AllowedActionVo("directPick", order.lines().stream()
                        .anyMatch(line -> line.unshippedQty().signum() > 0), null),
                new AllowedActionVo("ship", order.lines().stream()
                        .anyMatch(line -> line.shippingStagedQty().signum() > 0), null),
                new AllowedActionVo("manualComplete", order.lines().stream()
                        .noneMatch(line -> line.shippingStagedQty().signum() > 0), null));
    }

    private InventoryReservation requiredReservation(InventoryMutationResult result) {
        if (result == null || result.reservation() == null) {
            throw new ServiceUnavailableException("自动预留未返回预留事实");
        }
        return result.reservation();
    }

    private InventoryReservationAllocation requiredAllocation(InventoryMutationResult result) {
        if (result == null || result.allocations() == null || result.allocations().isEmpty()
                || result.allocations().getFirst() == null) {
            throw new ServiceUnavailableException("自动预留未返回库位分配");
        }
        return result.allocations().getFirst();
    }

    private BigDecimal sum(List<ReservationPart> parts) {
        return parts.stream().map(part -> part.allocation().activeQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void replaceLine(List<SalesOrderLine> lines, SalesOrderLine updated) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(updated.id())) {
                lines.set(i, updated);
                return;
            }
        }
        throw new ServiceUnavailableException("销售订单履约明细替换失败");
    }

    private void collectTransactions(InventoryMutationResult result, List<UUID> transactionIds) {
        if (result != null && result.transactions() != null) {
            result.transactions().stream().filter(transaction -> transaction != null && transaction.id() != null)
                    .forEach(transaction -> transactionIds.add(transaction.id()));
        }
    }

    private void requirePickLocations(LocationSnapshot source, LocationSnapshot shipping) {
        if (source.type() == LocationType.QualityHold || source.type() == LocationType.ReceivingStaging
                || source.type() == LocationType.Adjustment || source.type() == LocationType.ShippingStaging) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, "直接拣货来源库位类型不可用");
        }
        if (shipping.type() != LocationType.ShippingStaging) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, "拣货目标必须是发货暂存位");
        }
        if (!source.warehouseId().equals(shipping.warehouseId())) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, "拣货来源和发货暂存位不能跨仓");
        }
    }

    private void requireReturnTarget(LocationSnapshot target) {
        if (target.type() == LocationType.QualityHold || target.type() == LocationType.ReceivingStaging
                || target.type() == LocationType.Adjustment || target.type() == LocationType.ShippingStaging) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, "退回目标库位类型不可用");
        }
    }

    private void validatePickRequest(UUID operationId, PickTaskConfirmRequest request) {
        if (operationId == null || request == null || request.getSalesOrderId() == null
                || request.getLines() == null || request.getLines().isEmpty()) {
            throw new ValidationException("拣货必须提供销售订单和明细");
        }
        uniquePickLines(request.getLines());
    }

    private void validateReturnRequest(UUID operationId, PickTaskReturnRequest request) {
        if (operationId == null || request == null || request.getSalesOrderId() == null
                || request.getLines() == null || request.getLines().isEmpty()) {
            throw new ValidationException("退回拣货必须提供销售订单和明细");
        }
    }

    private BigDecimal positive(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 必须大于 0");
        }
        try {
            BigDecimal quantity = new BigDecimal(value.trim());
            if (quantity.signum() <= 0 || quantity.scale() > 6) {
                throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 必须为合法正数");
            }
            return quantity.setScale(6);
        } catch (NumberFormatException exception) {
            throw new SalesOrderException(SalesOrderErrorCode.SO_002, field + " 格式不正确");
        }
    }

    private Actor actor() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID userId = UserContextHolder.requireUserId();
        String sessionId = RequestContextHolder.getContext().getJti();
        String requestId = RequestContextHolder.getRequestId();
        if (sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            throw new ValidationException("缺失可信会话或请求上下文");
        }
        return new Actor(tenantId, userId, sessionId, requestId);
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ValidationException("Idempotency-Key 必须存在且不能超过 128 个字符");
        }
    }

    private String childKey(String parent, String suffix) {
        return "sales-" + UUID.nameUUIDFromBytes((parent + "|" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private String digest(String operation, Object value) {
        try {
            byte[] bytes = new ObjectMapper().registerModule(new JavaTimeModule())
                    .valueToTree(Map.of("operation", operation, "payload", value))
                    .toString().getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new ServiceUnavailableException("销售履约幂等载荷摘要生成失败", exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record Actor(UUID tenantId, UUID userId, String sessionId, String requestId) {
    }

    private record ReservationPart(InventoryReservation reservation,
                                   InventoryReservationAllocation allocation) {
    }

    private record InventoryReservationPageResult(
            List<InventoryReservationView> content,
            long total, int page, int size) {
        private boolean hasNext() {
            return page > 0 && size > 0 && (long) page * size < total;
        }
    }
}
