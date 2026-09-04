package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import com.ailearn.platform.core.inventory.domain.InventoryLocationRules;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.domain.LocationSnapshot;
import com.ailearn.platform.core.inventory.infrastructure.InventoryLocationPort;
import com.ailearn.platform.core.inventory.infrastructure.InventoryRepository;
import com.ailearn.platform.core.inventory.exception.InventoryErrorCode;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 库存唯一写应用服务，同时提供库存事实查询端口实现。
 * <p>
 * 该服务是采购、销售、MES 等领域写库存的唯一入口：先校验可信上下文和库位主数据，再在一个事务中
 * 按稳定顺序锁定余额、校验可用量、更新余额、维护预留分配并追加流水。幂等执行通过共享存储抽象
 * 保护，成功结果只在事务提交后缓存，回滚会释放幂等占用。
 * </p>
 */
@Service
public class InventoryApplicationService implements InventoryCommandService, InventoryQueryService {

    private static final Set<String> INCREASE_ACTIONS = Set.of("DECREASE", "MOVE", "RESERVE");
    private static final Set<String> DECREASE_ACTIONS = Set.of("INCREASE", "MOVE", "RESERVE");
    private static final Set<String> MOVE_ACTIONS = Set.of("MOVE", "RESERVE");
    private static final Set<String> RESERVE_ACTIONS = Set.of("RELEASE", "MOVE_RESERVATION_ALLOCATION");
    private static final Set<String> RELEASE_ACTIONS = Set.of("RESERVE");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final InventoryRepository repository;
    private final InventoryLocationPort locationPort;
    private final IdempotencyStorage idempotencyStorage;
    private final ObjectMapper objectMapper;

    /**
     * 创建库存应用服务。
     *
     * @param repository 库存余额、预留、分配和流水持久化边界
     * @param locationPort 主数据库位只读端口
     */
    public InventoryApplicationService(InventoryRepository repository, InventoryLocationPort locationPort) {
        this(repository, locationPort, new InMemoryIdempotencyStorage(),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建可替换幂等存储的库存应用服务。
     * 入参：库存 Repository、库位端口、幂等存储和 Jackson 映射器；出参：可被 Spring 管理的库存服务；
     * 流程：保存依赖，命令执行时由存储完成跨请求的幂等占用和结果重放。
     *
     * @param repository 库存余额、预留、分配和流水持久化边界
     * @param locationPort 主数据库位只读端口
     * @param idempotencyStorage 幂等执行记录存储
     * @param objectMapper 变更结果序列化器
     */
    @Autowired
    public InventoryApplicationService(InventoryRepository repository,
                                       InventoryLocationPort locationPort,
                                       IdempotencyStorage idempotencyStorage,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.locationPort = locationPort;
        this.idempotencyStorage = idempotencyStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * 增加指定库存维度的实物，并追加一条入库流水。
     * 入参：包含可信审计信息、库位和正数数量的增加命令；出参：更新后的余额及流水；流程：校验上下文和
     * 库位 -> 锁定余额 -> 校验不变量 -> 写余额和流水，失败由事务回滚并释放内存幂等占用。
     *
     * @param command 增加命令
     * @return 库存变更结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMutationResult increase(InventoryIncreaseCommand command) {
        return execute(command, context -> {
            LocationSnapshot location = activeLocation(context.tenantId(), command.dimension());
            InventoryLocationRules.requireIncreaseTarget(location, command.metadata());
            BigDecimal quantity = InventoryCommandSupport.positiveQuantity(command.quantity());
            InventoryBalance locked = requiredBalance(repository.lockOrCreateBalance(
                    context.tenantId(), command.dimension(), context.userId()));
            requireExpectedBalanceVersion(locked, command.expectedBalanceVersion());
            InventoryBalance updated = repository.updateBalance(locked,
                    locked.onHandQty().add(quantity), locked.reservedQty(), command.metadata().businessTime(),
                    context.userId());
            InventoryTransaction transaction = transaction("inventory:increase", command, context, null,
                    command.dimension(), quantity);
            return result("INCREASE", quantity, List.of(updated), null, List.of(),
                    List.of(repository.appendTransaction(transaction)), INCREASE_ACTIONS);
        });
    }

    /**
     * 减少指定库存维度的实物，并追加一条出库流水。
     * 入参：包含来源库位和正数数量的减少命令；出参：更新后的余额及流水；流程：校验库位 -> 锁定余额 ->
     * 只允许从 availableQty 扣减 -> 写余额和流水。
     *
     * @param command 减少命令
     * @return 库存变更结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMutationResult decrease(InventoryDecreaseCommand command) {
        return execute(command, context -> {
            LocationSnapshot location = activeLocation(context.tenantId(), command.dimension());
            InventoryLocationRules.requireDecreaseSource(location, command.metadata());
            BigDecimal quantity = InventoryCommandSupport.positiveQuantity(command.quantity());
            InventoryBalance locked = requiredBalance(repository.lockOrCreateBalance(
                    context.tenantId(), command.dimension(), context.userId()));
            requireExpectedBalanceVersion(locked, command.expectedBalanceVersion());
            requireAvailable(locked, quantity);
            InventoryBalance updated = repository.updateBalance(locked,
                    locked.onHandQty().subtract(quantity), locked.reservedQty(), command.metadata().businessTime(),
                    context.userId());
            InventoryTransaction transaction = transaction("inventory:decrease", command, context,
                    command.dimension(), null, quantity);
            return result("DECREASE", quantity, List.of(updated), null, List.of(),
                    List.of(repository.appendTransaction(transaction)), DECREASE_ACTIONS);
        });
    }

    /**
     * 在两个维度间移动实物，可选地同步移动等量预留分配。
     * 入参：来源、目标维度和正数数量，若携带 reservationId/allocationId 则必须成对出现；出参：两侧余额、
     * 受影响分配和移动流水；流程：校验两库位及稳定双行锁 -> 校验来源可用量和目标不变量 -> 更新余额、
     * 分配及流水。
     *
     * @param command 实物移动命令
     * @return 库存变更结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMutationResult move(InventoryMoveCommand command) {
        return execute(command, context -> {
            requireMoveIdentifiers(command.reservationId(), command.allocationId());
            InventoryDimension fromDimension = requiredDimension("fromDimension", command.fromDimension());
            InventoryDimension toDimension = requiredDimension("toDimension", command.toDimension());
            if (fromDimension.equals(toDimension)) {
                throw new ValidationException("来源和目标库存维度不能相同");
            }
            LocationSnapshot fromLocation = activeLocation(context.tenantId(), fromDimension);
            LocationSnapshot toLocation = activeLocation(context.tenantId(), toDimension);
            InventoryLocationRules.requireMove(fromLocation, toLocation);
            requireStableMoveIdentity(fromDimension, toDimension, command.metadata().transactionType(), true);
            BigDecimal quantity = InventoryCommandSupport.positiveQuantity(command.quantity());
            List<InventoryBalance> locked = repository.lockBalancesInStableOrder(context.tenantId(),
                    List.of(fromDimension, toDimension), context.userId());
            InventoryBalance source = findBalance(locked, fromDimension);
            InventoryBalance target = findBalance(locked, toDimension);
            InventoryReservation reservation = null;
            List<InventoryReservationAllocation> movedAllocations = List.of();
            if (command.reservationId() != null) {
                reservation = requiredReservation(repository.lockReservation(context.tenantId(), command.reservationId()));
                List<InventoryReservationAllocation> allocations = repository.lockActiveAllocations(
                        context.tenantId(), reservation.id(), command.allocationId(), fromDimension);
                InventoryReservationAllocation allocation = singleAllocation(allocations);
                if (allocation.activeQty().compareTo(quantity) < 0) {
                    throw new InventoryException(InventoryErrorCode.INV_005, "预留分配数量不足");
                }
                if (source.onHandQty().compareTo(quantity) < 0) {
                    throw new InventoryException(InventoryErrorCode.INV_001, "来源库位实物库存不足");
                }
                movedAllocations = repository.moveAllocation(allocation, toDimension, quantity, context.userId());
            } else {
                requireAvailable(source, quantity);
            }

            BigDecimal sourceReserved = reservation == null
                    ? source.reservedQty() : source.reservedQty().subtract(quantity);
            BigDecimal targetReserved = reservation == null
                    ? target.reservedQty() : target.reservedQty().add(quantity);
            InventoryBalance updatedSource = repository.updateBalance(source,
                    source.onHandQty().subtract(quantity), sourceReserved,
                    command.metadata().businessTime(), context.userId());
            InventoryBalance updatedTarget = repository.updateBalance(target,
                    target.onHandQty().add(quantity), targetReserved,
                    command.metadata().businessTime(), context.userId());
            InventoryTransaction transaction = transaction("inventory:move", command, context,
                    fromDimension, toDimension, quantity);
            return result("MOVE", quantity, List.of(updatedSource, updatedTarget), reservation,
                    movedAllocations, List.of(repository.appendTransaction(transaction)), MOVE_ACTIONS);
        });
    }

    /**
     * 创建预留并生成首个库位分配。
     * 入参：可分配库位、来源单据和正数数量；出参：更新余额、预留、分配及流水；流程：校验库位不可为
     * QualityHold/ReceivingStaging/Adjustment -> 锁余额并检查 availableQty -> 写预留、分配、余额和流水。
     *
     * @param command 预留命令
     * @return 库存变更结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMutationResult reserve(InventoryReserveCommand command) {
        return execute(command, context -> {
            LocationSnapshot location = activeLocation(context.tenantId(), command.dimension());
            InventoryLocationRules.requireAllocatable(location);
            BigDecimal quantity = InventoryCommandSupport.positiveQuantity(command.quantity());
            InventoryBalance locked = requiredBalance(repository.lockOrCreateBalance(
                    context.tenantId(), command.dimension(), context.userId()));
            requireAvailable(locked, quantity);
            OffsetDateTime now = command.metadata().businessTime();
            UUID reservationId = UUID.randomUUID();
            InventoryReservation reservation = new InventoryReservation(reservationId, context.tenantId(),
                    normalizeReservationNo(command.reservationNo(), reservationId), command.metadata().sourceType(),
                    command.metadata().sourceId(), command.metadata().sourceLineId(), quantity,
                    InventoryInvariant.ZERO, "Active", 0L, now, now);
            InventoryReservationAllocation allocation = new InventoryReservationAllocation(
                    UUID.randomUUID(), context.tenantId(), reservationId, command.dimension(), quantity,
                    InventoryInvariant.ZERO, 0L, now, now);
            InventoryBalance updated = repository.updateBalance(locked, locked.onHandQty(),
                    locked.reservedQty().add(quantity), now, context.userId());
            repository.insertReservation(reservation, context.userId());
            InventoryReservationAllocation insertedAllocation = repository.insertAllocation(allocation, context.userId());
            InventoryTransaction transaction = transaction("inventory:reserve", command, context, null,
                    command.dimension(), quantity);
            return result("RESERVE", quantity, List.of(updated), reservation,
                    List.of(insertedAllocation), List.of(repository.appendTransaction(transaction)), RESERVE_ACTIONS);
        });
    }

    /**
     * 释放一个预留在指定分配维度上的数量。
     * 入参：预留、库位、可选分配 ID 和正数释放量；出参：更新余额、预留、分配及流水；流程：锁余额和预留
     * -> 按分配 ID 稳定释放 -> 同步减少余额 reservedQty -> 更新预留状态并追加流水。
     *
     * @param command 预留释放命令
     * @return 库存变更结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMutationResult release(InventoryReleaseCommand command) {
        return execute(command, context -> {
            if (command.reservationId() == null) {
                throw new ValidationException("reservationId 不能为空");
            }
            LocationSnapshot location = activeLocation(context.tenantId(), command.dimension());
            InventoryLocationRules.requireAllocatable(location);
            BigDecimal quantity = InventoryCommandSupport.positiveQuantity(command.quantity());
            InventoryBalance locked = requiredBalance(repository.lockOrCreateBalance(
                    context.tenantId(), command.dimension(), context.userId()));
            InventoryReservation reservation = requiredReservation(repository.lockReservation(
                    context.tenantId(), command.reservationId()));
            if (reservation.activeQty().compareTo(quantity) < 0) {
                throw new InventoryException(InventoryErrorCode.INV_005, "预留有效数量不足");
            }
            List<InventoryReservationAllocation> candidates = repository.lockActiveAllocations(
                    context.tenantId(), reservation.id(), command.allocationId(), command.dimension());
            List<InventoryReservationAllocation> released = releaseAllocations(candidates, quantity, context.userId());
            if (locked.reservedQty().compareTo(quantity) < 0) {
                throw new InventoryException(InventoryErrorCode.INV_001, "库存有效预留数量不足");
            }
            InventoryBalance updated = repository.updateBalance(locked, locked.onHandQty(),
                    locked.reservedQty().subtract(quantity), command.metadata().businessTime(), context.userId());
            InventoryReservation updatedReservation = repository.releaseReservation(reservation,
                    quantity, context.userId());
            InventoryTransaction transaction = transaction("inventory:release", command, context,
                    command.dimension(), null, quantity);
            return result("RELEASE", quantity, List.of(updated), updatedReservation, released,
                    List.of(repository.appendTransaction(transaction)), RELEASE_ACTIONS);
        });
    }

    /**
     * 移动预留分配而不改变企业实物总量。
     * 入参：预留、源/目标可分配库位和正数数量；出参：两侧余额、移动分配和流水；流程：锁定两侧余额及
     * 分配 -> 检查目标可用量 -> 等量调整 reservedQty -> 持久化分配位置并追加流水。
     *
     * @param command 预留分配移动命令
     * @return 库存变更结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMutationResult moveReservationAllocation(InventoryAllocationMoveCommand command) {
        return execute(command, context -> {
            if (command.reservationId() == null || command.allocationId() == null) {
                throw new ValidationException("reservationId 和 allocationId 不能为空");
            }
            InventoryDimension fromDimension = requiredDimension("fromDimension", command.fromDimension());
            InventoryDimension toDimension = requiredDimension("toDimension", command.toDimension());
            if (fromDimension.equals(toDimension)) {
                throw new ValidationException("来源和目标库存维度不能相同");
            }
            LocationSnapshot fromLocation = activeLocation(context.tenantId(), fromDimension);
            LocationSnapshot toLocation = activeLocation(context.tenantId(), toDimension);
            InventoryLocationRules.requireMove(fromLocation, toLocation);
            requireStableMoveIdentity(fromDimension, toDimension, command.metadata().transactionType(), false);
            InventoryLocationRules.requireAllocatable(fromLocation);
            InventoryLocationRules.requireAllocatable(toLocation);
            BigDecimal quantity = InventoryCommandSupport.positiveQuantity(command.quantity());
            List<InventoryBalance> locked = repository.lockBalancesInStableOrder(context.tenantId(),
                    List.of(fromDimension, toDimension), context.userId());
            InventoryBalance source = findBalance(locked, fromDimension);
            InventoryBalance target = findBalance(locked, toDimension);
            InventoryReservation reservation = requiredReservation(repository.lockReservation(
                    context.tenantId(), command.reservationId()));
            InventoryReservationAllocation allocation = singleAllocation(repository.lockActiveAllocations(
                    context.tenantId(), reservation.id(), command.allocationId(), fromDimension));
            if (allocation.activeQty().compareTo(quantity) < 0) {
                throw new InventoryException(InventoryErrorCode.INV_005, "预留分配数量不足");
            }
            if (target.availableQty().compareTo(quantity) < 0) {
                throw new InventoryException(InventoryErrorCode.INV_001, "目标库位可用库存不足以承接预留");
            }
            List<InventoryReservationAllocation> moved = repository.moveAllocation(
                    allocation, toDimension, quantity, context.userId());
            InventoryBalance updatedSource = repository.updateBalance(source, source.onHandQty(),
                    source.reservedQty().subtract(quantity), command.metadata().businessTime(), context.userId());
            InventoryBalance updatedTarget = repository.updateBalance(target, target.onHandQty(),
                    target.reservedQty().add(quantity), command.metadata().businessTime(), context.userId());
            InventoryTransaction transaction = transaction("inventory:move-reservation-allocation", command, context,
                    fromDimension, toDimension, quantity);
            return result("MOVE_RESERVATION_ALLOCATION", quantity,
                    List.of(updatedSource, updatedTarget), reservation, moved,
                    List.of(repository.appendTransaction(transaction)), MOVE_ACTIONS);
        });
    }

    /**
     * 按可信租户查询余额。
     * 入参：可选租户断言、维度筛选和分页；出参：库存余额分页；流程：校验分页和租户断言后委托查询边界。
     *
     * @param query 余额查询条件
     * @return 租户隔离余额分页
     */
    @Override
    @Transactional(readOnly = true)
    public InventoryBalancePage queryBalances(InventoryBalanceQuery query) {
        if (query == null) {
            throw new ValidationException("余额查询条件不能为空");
        }
        InventoryCommandSupport.page(query.page(), query.size());
        UUID tenantId = InventoryCommandSupport.trustedQueryTenant(query.tenantId());
        return repository.queryBalances(tenantId, query);
    }

    /**
     * 按可信租户查询预留和分配。
     * 入参：预留、来源、维度和分页筛选；出参：租户隔离的预留分页；流程：校验查询租户和分页后委托边界。
     *
     * @param query 预留查询条件
     * @return 租户隔离预留分页
     */
    @Override
    @Transactional(readOnly = true)
    public InventoryReservationPage queryReservations(InventoryReservationQuery query) {
        if (query == null) {
            throw new ValidationException("预留查询条件不能为空");
        }
        InventoryCommandSupport.page(query.page(), query.size());
        UUID tenantId = InventoryCommandSupport.trustedQueryTenant(query.tenantId());
        return repository.queryReservations(tenantId, query);
    }

    /**
     * 按可信租户查询只追加库存流水。
     * 入参：交易、来源、维度、时间和分页筛选；出参：租户隔离流水分页；流程：校验查询租户和分页后委托边界。
     *
     * @param query 流水查询条件
     * @return 租户隔离流水分页
     */
    @Override
    @Transactional(readOnly = true)
    public InventoryTransactionPage queryTransactions(InventoryTransactionQuery query) {
        if (query == null) {
            throw new ValidationException("流水查询条件不能为空");
        }
        InventoryCommandSupport.page(query.page(), query.size());
        UUID tenantId = InventoryCommandSupport.trustedQueryTenant(query.tenantId());
        return repository.queryTransactions(tenantId, query);
    }

    /**
     * 执行带事务协调的幂等命令。
     * 入参：库存命令及已通过可信上下文的业务函数；出参：首次成功结果或同载荷重放结果；流程：读取并
     * 校验服务端摘要，原子登记操作域 key/digest，处理中重复请求拒绝，成功结果在事务提交前缓存，失败释放占用。
     */
    private <C extends InventoryCommand> InventoryMutationResult execute(
            C command, Function<InventoryCommandSupport.TrustedCommandContext, InventoryMutationResult> action) {
        InventoryCommandSupport.TrustedCommandContext context = InventoryCommandSupport.validate(command);
        String operation = operation(command);
        String key = command.metadata().idempotencyKey();
        String digest = canonicalDigest(command, operation);
        IdempotentRecord existing = idempotencyStorage.getRecord(operation, key, context.tenantId()).orElse(null);
        if (existing != null) {
            return replayOrReject(existing, key, digest);
        }
        if (!idempotencyStorage.tryAcquire(operation, key, context.tenantId(), IDEMPOTENCY_TTL, digest)) {
            IdempotentRecord raced = idempotencyStorage.getRecord(operation, key, context.tenantId()).orElse(null);
            if (raced == null) {
                throw new InventoryException(InventoryErrorCode.INV_002, "幂等命令正在处理中");
            }
            return replayOrReject(raced, key, digest);
        }
        registerRollbackCleanup(operation, key, context.tenantId());
        try {
            InventoryMutationResult result = action.apply(context);
            completeBeforeCommit(operation, key, context.tenantId(), result);
            return result;
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                idempotencyStorage.fail(operation, key, context.tenantId(), exception.getMessage());
            }
            throw exception;
        }
    }

    /**
     * 为每一种库存命令生成稳定的操作域。
     * 入参：库存命令；出参：用于幂等存储和流水键命名空间的操作名；流程：按命令类型显式映射，
     * 禁止未知命令静默复用其他操作的幂等键。
     *
     * @param command 库存命令
     * @return 操作域
     */
    private String operation(InventoryCommand command) {
        if (command instanceof InventoryIncreaseCommand) {
            return "inventory:increase";
        }
        if (command instanceof InventoryDecreaseCommand) {
            return "inventory:decrease";
        }
        if (command instanceof InventoryMoveCommand) {
            return "inventory:move";
        }
        if (command instanceof InventoryReserveCommand) {
            return "inventory:reserve";
        }
        if (command instanceof InventoryReleaseCommand) {
            return "inventory:release";
        }
        if (command instanceof InventoryAllocationMoveCommand) {
            return "inventory:move-reservation-allocation";
        }
        throw new ValidationException("未知库存命令类型");
    }

    /**
     * 由服务端从完整业务命令生成载荷摘要，不信任客户端自报的 payloadDigest。
     * 入参：已通过可信上下文校验的库存命令和操作域；出参：SHA-256 摘要；流程：按固定字段顺序收集
     * 业务维度、数量、来源和预留标识后序列化，故同 Key 改变业务字段必然冲突。
     *
     * @param command 库存命令
     * @param operation 操作域
     * @return 服务端载荷摘要
     */
    private String canonicalDigest(InventoryCommand command, String operation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        InventoryCommandMetadata metadata = command.metadata();
        payload.put("operation", operation);
        payload.put("tenantId", metadata.tenantId());
        payload.put("sourceType", metadata.sourceType());
        payload.put("sourceId", metadata.sourceId());
        payload.put("sourceLineId", metadata.sourceLineId());
        payload.put("transactionType", metadata.transactionType());
        payload.put("businessTime", metadata.businessTime());
        payload.put("quantity", command.quantity());
        if (command instanceof InventoryIncreaseCommand increase) {
            payload.put("dimension", increase.dimension());
            payload.put("expectedBalanceVersion", increase.expectedBalanceVersion());
        } else if (command instanceof InventoryDecreaseCommand decrease) {
            payload.put("dimension", decrease.dimension());
            payload.put("expectedBalanceVersion", decrease.expectedBalanceVersion());
        } else if (command instanceof InventoryMoveCommand move) {
            payload.put("fromDimension", move.fromDimension());
            payload.put("toDimension", move.toDimension());
            payload.put("reservationId", move.reservationId());
            payload.put("allocationId", move.allocationId());
        } else if (command instanceof InventoryReserveCommand reserve) {
            payload.put("dimension", reserve.dimension());
            payload.put("reservationNo", reserve.reservationNo());
        } else if (command instanceof InventoryReleaseCommand release) {
            payload.put("reservationId", release.reservationId());
            payload.put("dimension", release.dimension());
            payload.put("allocationId", release.allocationId());
        } else if (command instanceof InventoryAllocationMoveCommand allocationMove) {
            payload.put("reservationId", allocationMove.reservationId());
            payload.put("allocationId", allocationMove.allocationId());
            payload.put("fromDimension", allocationMove.fromDimension());
            payload.put("toDimension", allocationMove.toDimension());
        }
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ServiceUnavailableException("库存幂等载荷摘要生成失败", exception);
        }
    }

    /**
     * 处理已存在的幂等记录。
     * 入参：历史记录、当前幂等键和载荷摘要；出参：同载荷成功结果或抛出受控冲突；流程：先比对摘要，
     * 再按 PENDING/SUCCESS 状态拒绝并发或反序列化首次成功结果。
     *
     * @param record 历史幂等记录
     * @param key 当前幂等键
     * @param digest 当前载荷摘要
     * @return 首次成功结果
     */
    private InventoryMutationResult replayOrReject(IdempotentRecord record, String key, String digest) {
        if (!digest.equals(record.getRequestHash())) {
            throw new InventoryException(InventoryErrorCode.INV_002,
                    "同一幂等键的 payloadDigest 不一致");
        }
        if (record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null || record.getResponseBody().isBlank()) {
            throw new InventoryException(InventoryErrorCode.INV_002, "幂等命令正在处理中");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), InventoryMutationResult.class);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("幂等结果缓存不可解析", exception);
        }
    }

    /**
     * 在当前事务回滚时释放幂等占用，避免失败命令永久阻塞重试。
     *
     * @param key 幂等键
     * @param tenantId 可信租户
     */
    private void registerRollbackCleanup(String operation, String key, UUID tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    idempotencyStorage.fail(operation, key, tenantId, "库存事务未提交");
                }
            }
        });
    }

    /**
     * 在事务提交前缓存首次成功响应；无事务的纯调用立即完成记录。
     *
     * @param key 幂等键
     * @param tenantId 可信租户
     * @param result 首次库存变更结果
     */
    private void completeBeforeCommit(String operation, String key, UUID tenantId, InventoryMutationResult result) {
        String responseBody = serialize(result);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            idempotencyStorage.complete(operation, key, tenantId, responseBody, IDEMPOTENCY_TTL);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (!readOnly) {
                    idempotencyStorage.complete(operation, key, tenantId, responseBody, IDEMPOTENCY_TTL);
                }
            }
        });
    }

    /**
     * 序列化幂等重放所需的结果。
     *
     * @param result 库存变更结果
     * @return JSON 响应体
     */
    private String serialize(InventoryMutationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("库存幂等结果无法缓存", exception);
        }
    }

    /**
     * 读取并校验租户库位。
     * 入参：可信租户和库存维度；出参：启用库位快照；流程：读取主数据后统一执行租户、ID、状态校验。
     */
    private LocationSnapshot activeLocation(UUID tenantId, InventoryDimension dimension) {
        InventoryDimension required = requiredDimension("dimension", dimension);
        LocationSnapshot snapshot = locationPort.findByTenantIdAndId(tenantId, required.locationId());
        InventoryLocationRules.requireActive(snapshot, required.locationId(), tenantId);
        if (snapshot.warehouseId() != null && !required.warehouseId().equals(snapshot.warehouseId())) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "库存维度仓库与库位所属仓库不一致");
        }
        return snapshot;
    }

    /**
     * 校验移动不会篡改产品和批次身份，并限制跨仓移动只能走明确的调拨交易。
     * 入参：来源/目标维度、交易类型和是否为普通实物移动；出参：无；流程：先比较产品与批次，
     * 再按交易语义判断仓库变化，预留分配移动始终禁止跨仓。
     *
     * @param fromDimension 来源维度
     * @param toDimension 目标维度
     * @param transactionType 交易类型
     * @param ordinaryPhysicalMove 是否为普通实物移动
     */
    private void requireStableMoveIdentity(InventoryDimension fromDimension,
                                            InventoryDimension toDimension,
                                            String transactionType,
                                            boolean ordinaryPhysicalMove) {
        if (!fromDimension.productId().equals(toDimension.productId())
                || !fromDimension.normalizedLotNo().equals(toDimension.normalizedLotNo())) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "库存移动不能改变产品或批次身份");
        }
        boolean transfer = "TRANSFER".equalsIgnoreCase(transactionType);
        if (!fromDimension.warehouseId().equals(toDimension.warehouseId())
                && (!ordinaryPhysicalMove || !transfer)) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "跨仓库存移动必须使用调拨交易");
        }
    }

    /**
     * 校验余额存在。
     * 入参：Repository 锁定结果；出参：非空余额；流程：数据库未返回行时以 503 暴露依赖异常。
     */
    private InventoryBalance requiredBalance(InventoryBalance balance) {
        if (balance == null) {
            throw new ServiceUnavailableException("库存余额行不可用");
        }
        return balance;
    }

    /**
     * 校验库存可用量足够。
     * 入参：已锁余额和正数扣减量；出参：无；流程：使用 onHand-reserved 公式拒绝超卖或破坏不变量。
     */
    private void requireAvailable(InventoryBalance balance, BigDecimal quantity) {
        if (balance.availableQty().compareTo(quantity) < 0) {
            throw new InventoryException(InventoryErrorCode.INV_001, "可用库存不足");
        }
    }

    /**
     * 在余额行已加锁后校验调用方快照版本，阻止盘点等基于快照的调整覆盖并发库存事实。
     * 入参：已加锁余额和可选期望版本；出参：版本匹配时无返回；流程：只对携带版本的命令执行
     * 比较，版本不一致返回统一乐观锁错误。
     *
     * @param balance 已加锁的当前余额
     * @param expectedVersion 调用方读取到的余额版本
     */
    private void requireExpectedBalanceVersion(InventoryBalance balance, Long expectedVersion) {
        if (expectedVersion != null && balance.version() != expectedVersion) {
            throw new InventoryException(InventoryErrorCode.INV_003,
                    "余额版本已变化，请重新读取后重试");
        }
    }

    /**
     * 从稳定锁结果中取得指定维度余额。
     * 入参：锁定余额集合和目标维度；出参：目标余额；流程：按完整维度匹配，避免依赖集合返回顺序。
     */
    private InventoryBalance findBalance(Collection<InventoryBalance> balances, InventoryDimension dimension) {
        return balances.stream().filter(balance -> dimension.equals(balance.dimension())).findFirst()
                .orElseThrow(() -> new ServiceUnavailableException("库存双余额锁定结果不完整"));
    }

    /**
     * 校验预留存在且属于已确认租户。
     * 入参：Repository 锁定结果；出参：非空预留；流程：缺失时返回 404，跨租户记录不会被找到。
     */
    private InventoryReservation requiredReservation(InventoryReservation reservation) {
        if (reservation == null) {
            throw new NotFoundException("库存预留");
        }
        return reservation;
    }

    /**
     * 确认只选中一条有效分配。
     * 入参：按租户、预留、维度和可选 ID 锁定的分配；出参：唯一分配；流程：无记录报 INV_005，多记录报冲突。
     */
    private InventoryReservationAllocation singleAllocation(List<InventoryReservationAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new InventoryException(InventoryErrorCode.INV_005, "未找到匹配的有效预留分配");
        }
        if (allocations.size() != 1) {
            throw new InventoryException(InventoryErrorCode.INV_003, "预留分配选择不唯一");
        }
        return allocations.get(0);
    }

    /**
     * 按分配 ID 顺序释放数量。
     * 入参：已锁定的有效分配和总释放量；出参：更新后的分配集合；流程：逐条调用 Repository，数量不足时整体回滚。
     */
    private List<InventoryReservationAllocation> releaseAllocations(
            List<InventoryReservationAllocation> candidates, BigDecimal quantity, UUID operatorId) {
        if (candidates == null || candidates.isEmpty()) {
            throw new InventoryException(InventoryErrorCode.INV_005, "未找到匹配的有效预留分配");
        }
        BigDecimal remaining = quantity;
            List<InventoryReservationAllocation> released = new ArrayList<>();
        for (InventoryReservationAllocation allocation : candidates) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal releaseQty = remaining.min(allocation.activeQty());
            if (releaseQty.signum() > 0) {
                released.add(repository.releaseAllocation(allocation, releaseQty, operatorId));
                remaining = remaining.subtract(releaseQty);
            }
        }
        if (remaining.signum() != 0) {
            throw new InventoryException(InventoryErrorCode.INV_005, "预留分配有效数量不足");
        }
        return List.copyOf(released);
    }

    /**
     * 校验实物移动的可选预留标识必须成对出现。
     */
    private void requireMoveIdentifiers(UUID reservationId, UUID allocationId) {
        if ((reservationId == null) != (allocationId == null)) {
            throw new ValidationException("reservationId 和 allocationId 必须同时提供或同时省略");
        }
    }

    /**
     * 统一校验库存维度，避免辅助方法产生空指针。
     */
    private InventoryDimension requiredDimension(String fieldName, InventoryDimension dimension) {
        if (dimension == null) {
            throw new ValidationException(fieldName + " 不能为空");
        }
        return dimension;
    }

    /**
     * 生成只追加库存流水。
     * 入参：命令元数据、可信上下文、来源/目标维度和数量；出参：新流水事实；流程：复制可信审计字段并生成
     * 租户内唯一业务编号，维度为空的一侧用空批次满足数据库非空默认契约。
     */
    private InventoryTransaction transaction(String operation,
                                             InventoryCommand command,
                                             InventoryCommandSupport.TrustedCommandContext context,
                                             InventoryDimension fromDimension,
                                             InventoryDimension toDimension,
                                             BigDecimal quantity) {
        InventoryCommandMetadata metadata = command.metadata();
        return new InventoryTransaction(UUID.randomUUID(), context.tenantId(),
                "INV-" + UUID.randomUUID(), metadata.transactionType(), metadata.sourceType(), metadata.sourceId(),
                metadata.sourceLineId(), fromDimension, toDimension, quantity, metadata.businessTime(),
                context.userId(), context.sessionId(), context.requestId(),
                IdempotencyStorage.scopedKey(operation, metadata.idempotencyKey()),
                canonicalDigest(command, operation));
    }

    /**
     * 规范化预留业务编号，避免超过 V2 字段长度。
     */
    private String normalizeReservationNo(String reservationNo, UUID reservationId) {
        if (reservationNo == null || reservationNo.isBlank()) {
            return "RES-" + reservationId;
        }
        String normalized = reservationNo.trim();
        if (normalized.length() > 64) {
            throw new ValidationException("reservationNo 长度不能超过 64 个字符");
        }
        return normalized;
    }

    /**
     * 构造统一变更结果。
     */
    private InventoryMutationResult result(String operation, BigDecimal quantity,
                                           List<InventoryBalance> balances,
                                           InventoryReservation reservation,
                                           List<InventoryReservationAllocation> allocations,
                                           List<InventoryTransaction> transactions,
                                           Set<String> allowedActions) {
        return new InventoryMutationResult(operation, quantity, balances, reservation,
                allocations, transactions, allowedActions);
    }
}
