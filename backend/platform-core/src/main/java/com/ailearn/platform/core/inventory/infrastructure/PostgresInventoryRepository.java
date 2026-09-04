package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryReservationPage;
import com.ailearn.platform.core.inventory.application.InventoryReservationQuery;
import com.ailearn.platform.core.inventory.application.InventoryReservationView;
import com.ailearn.platform.core.inventory.application.InventoryTransactionPage;
import com.ailearn.platform.core.inventory.application.InventoryTransactionQuery;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryReservationAllocation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.inventory.exception.InventoryErrorCode;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL 库存持久化实现。
 * <p>
 * 该类是 inventory 目录唯一的数据库写 Repository：余额更新、预留/分配变更和流水追加均在上层
 * {@code @Transactional} 应用服务事务内执行。所有 Mapper 参数都带租户条件，余额通过 {@code FOR UPDATE}
 * 加行锁，双余额由稳定维度顺序锁定。
 * </p>
 */
@Repository
public class PostgresInventoryRepository implements InventoryRepository {

    private final InventoryBalanceMapper balanceMapper;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryAllocationMapper allocationMapper;
    private final InventoryTransactionMapper transactionMapper;

    /**
     * 创建 PostgreSQL 库存 Repository。
     *
     * @param balanceMapper 余额 Mapper
     * @param reservationMapper 预留 Mapper
     * @param allocationMapper 分配 Mapper
     * @param transactionMapper 流水 Mapper
     */
    public PostgresInventoryRepository(InventoryBalanceMapper balanceMapper,
                                       InventoryReservationMapper reservationMapper,
                                       InventoryAllocationMapper allocationMapper,
                                       InventoryTransactionMapper transactionMapper) {
        this.balanceMapper = balanceMapper;
        this.reservationMapper = reservationMapper;
        this.allocationMapper = allocationMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 按维度锁定余额；不存在时先用唯一键创建零余额行，再重新锁定。
     *
     * @param tenantId 可信租户
     * @param dimension 库存维度
     * @param operatorId 操作用户
     * @return 已锁定余额
     */
    @Override
    public InventoryBalance lockOrCreateBalance(UUID tenantId, InventoryDimension dimension, UUID operatorId) {
        return database(() -> {
            InventoryBalanceRow row = balanceMapper.selectForUpdate(tenantId, dimension.productId(),
                    dimension.warehouseId(), dimension.locationId(), dimension.lotNo());
            if (row == null) {
                InventoryBalanceRow newRow = new InventoryBalanceRow();
                newRow.setId(UUID.randomUUID());
                newRow.setTenantId(tenantId);
                newRow.setProductId(dimension.productId());
                newRow.setWarehouseId(dimension.warehouseId());
                newRow.setLocationId(dimension.locationId());
                newRow.setLotNo(dimension.lotNo());
                newRow.setOnHandQty(InventoryInvariant.ZERO);
                newRow.setReservedQty(InventoryInvariant.ZERO);
                newRow.setVersion(0L);
                balanceMapper.insertIfAbsent(newRow, operatorId);
                row = balanceMapper.selectForUpdate(tenantId, dimension.productId(),
                        dimension.warehouseId(), dimension.locationId(), dimension.lotNo());
            }
            if (row == null) {
                throw new ServiceUnavailableException("库存余额行创建后无法重新锁定");
            }
            return toBalance(row);
        });
    }

    /**
     * 对去重后的维度按稳定键排序并逐行加锁，避免相反顺序造成数据库死锁。
     *
     * @param tenantId 可信租户
     * @param dimensions 待锁定维度
     * @param operatorId 操作用户
     * @return 按稳定键顺序排列的余额
     */
    @Override
    public List<InventoryBalance> lockBalancesInStableOrder(UUID tenantId,
                                                            Collection<InventoryDimension> dimensions,
                                                            UUID operatorId) {
        if (dimensions == null || dimensions.isEmpty()) {
            return List.of();
        }
        List<InventoryDimension> sorted = dimensions.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(dimension -> dimension.lockKey(tenantId)))
                .toList();
        List<InventoryBalance> locked = new ArrayList<>(sorted.size());
        for (InventoryDimension dimension : sorted) {
            locked.add(lockOrCreateBalance(tenantId, dimension, operatorId));
        }
        return List.copyOf(locked);
    }

    /**
     * 使用余额版本条件同时写入 onHand/reserved。
     *
     * @param balance 已锁定余额
     * @param onHandQty 新实物数量
     * @param reservedQty 新预留数量
     * @param businessTime 最近事实时间
     * @param operatorId 操作用户
     * @return 更新后的余额
     */
    @Override
    public InventoryBalance updateBalance(InventoryBalance balance,
                                           BigDecimal onHandQty,
                                           BigDecimal reservedQty,
                                           OffsetDateTime businessTime,
                                           UUID operatorId) {
        InventoryInvariant.requireBalanced(onHandQty, reservedQty);
        return database(() -> {
            InventoryBalanceRow row = toRow(balance);
            row.setOnHandQty(onHandQty);
            row.setReservedQty(reservedQty);
            row.setLastTransactionAt(businessTime);
            int updated = balanceMapper.updateAmounts(row, operatorId);
            if (updated != 1) {
                throw new InventoryException(InventoryErrorCode.INV_003,
                        "余额版本已变化，请重新读取后重试");
            }
            return new InventoryBalance(balance.id(), balance.tenantId(), balance.dimension(),
                    onHandQty, reservedQty, balance.version() + 1, businessTime);
        });
    }

    /**
     * 锁定租户内预留。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @return 预留或 null
     */
    @Override
    public InventoryReservation lockReservation(UUID tenantId, UUID reservationId) {
        return database(() -> toReservation(reservationMapper.selectForUpdate(tenantId, reservationId)));
    }

    /**
     * 插入预留事实。
     *
     * @param reservation 预留
     * @return 已写入预留
     */
    @Override
    public InventoryReservation insertReservation(InventoryReservation reservation) {
        return insertReservation(reservation, reservation.sourceId());
    }

    /**
     * 使用可信操作人写入预留审计字段。
     *
     * @param reservation 预留事实
     * @param operatorId 操作用户
     * @return 已写入预留
     */
    @Override
    public InventoryReservation insertReservation(InventoryReservation reservation, UUID operatorId) {
        return database(() -> {
            InventoryReservationRow row = toRow(reservation);
            if (reservationMapper.insert(row, operatorId) != 1) {
                throw new ServiceUnavailableException("库存预留写入失败");
            }
            return reservation;
        });
    }

    /**
     * 插入分配事实。
     *
     * @param allocation 分配
     * @return 已写入分配
     */
    @Override
    public InventoryReservationAllocation insertAllocation(InventoryReservationAllocation allocation) {
        return insertAllocation(allocation, allocation.tenantId());
    }

    /**
     * 使用可信操作人写入分配审计字段。
     *
     * @param allocation 分配事实
     * @param operatorId 操作用户
     * @return 已写入分配
     */
    @Override
    public InventoryReservationAllocation insertAllocation(InventoryReservationAllocation allocation,
                                                            UUID operatorId) {
        return database(() -> {
            InventoryAllocationRow row = toRow(allocation);
            if (allocationMapper.insert(row, operatorId) != 1) {
                throw new ServiceUnavailableException("库存预留分配写入失败");
            }
            return allocation;
        });
    }

    /**
     * 锁定预留下指定维度的有效分配。
     *
     * @param tenantId 可信租户
     * @param reservationId 预留 ID
     * @param allocationId 可选分配 ID
     * @param dimension 分配维度
     * @return 已锁定分配
     */
    @Override
    public List<InventoryReservationAllocation> lockActiveAllocations(UUID tenantId,
                                                                       UUID reservationId,
                                                                       UUID allocationId,
                                                                       InventoryDimension dimension) {
        return database(() -> allocationMapper.selectActiveForUpdate(tenantId, reservationId, allocationId,
                        dimension.productId(), dimension.warehouseId(), dimension.locationId(), dimension.lotNo())
                .stream().map(this::toAllocation).toList());
    }

    /**
     * 释放分配的有效数量。
     *
     * @param allocation 已锁定分配
     * @param quantity 释放数量
     * @param operatorId 操作用户
     * @return 更新后的分配
     */
    @Override
    public InventoryReservationAllocation releaseAllocation(InventoryReservationAllocation allocation,
                                                             BigDecimal quantity,
                                                             UUID operatorId) {
        return database(() -> {
            if (allocationMapper.release(toRow(allocation), quantity, operatorId) != 1) {
                throw new InventoryException(InventoryErrorCode.INV_003,
                        "预留分配版本已变化，请重试");
            }
            return new InventoryReservationAllocation(allocation.id(), allocation.tenantId(),
                    allocation.reservationId(), allocation.dimension(), allocation.allocatedQty(),
                    allocation.releasedQty().add(quantity), allocation.version() + 1,
                    allocation.createdAt(), OffsetDateTime.now());
        });
    }

    /**
     * 移动整条或部分预留分配，部分移动通过新增目标分配保留事实轨迹。
     *
     * @param allocation 已锁定源分配
     * @param targetDimension 目标维度
     * @param quantity 移动数量
     * @param operatorId 操作用户
     * @return 受影响的源/目标分配
     */
    @Override
    public List<InventoryReservationAllocation> moveAllocation(InventoryReservationAllocation allocation,
                                                                InventoryDimension targetDimension,
                                                                BigDecimal quantity,
                                                                UUID operatorId) {
        BigDecimal activeQty = allocation.activeQty();
        if (quantity.compareTo(activeQty) > 0) {
            throw new InventoryException(InventoryErrorCode.INV_005,
                    "移动数量超过分配的有效数量");
        }
        return database(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            if (quantity.compareTo(activeQty) == 0) {
                if (allocationMapper.moveWhole(toRow(allocation), activeQty,
                        targetDimension.productId(), targetDimension.warehouseId(),
                        targetDimension.locationId(), targetDimension.lotNo(), operatorId) != 1) {
                    throw new InventoryException(InventoryErrorCode.INV_003,
                            "预留分配版本已变化，请重试");
                }
                return List.of(new InventoryReservationAllocation(allocation.id(), allocation.tenantId(),
                        allocation.reservationId(), targetDimension, allocation.allocatedQty(),
                        allocation.releasedQty(), allocation.version() + 1,
                        allocation.createdAt(), now));
            }

            if (allocationMapper.reduceAllocated(toRow(allocation), quantity, operatorId) != 1) {
                throw new InventoryException(InventoryErrorCode.INV_003,
                        "预留分配版本已变化，请重试");
            }
            InventoryReservationAllocation target = new InventoryReservationAllocation(
                    UUID.randomUUID(), allocation.tenantId(), allocation.reservationId(), targetDimension,
                    quantity, InventoryInvariant.ZERO, 0L, now, now);
            if (allocationMapper.insert(toRow(target), operatorId) != 1) {
                throw new ServiceUnavailableException("部分预留分配移动写入失败");
            }
            InventoryReservationAllocation source = new InventoryReservationAllocation(
                    allocation.id(), allocation.tenantId(), allocation.reservationId(), allocation.dimension(),
                    allocation.allocatedQty().subtract(quantity), allocation.releasedQty(),
                    allocation.version() + 1, allocation.createdAt(), now);
            return List.of(source, target);
        });
    }

    /**
     * 释放预留总量并推导状态。
     *
     * @param reservation 已锁定预留
     * @param quantity 释放数量
     * @param operatorId 操作用户
     * @return 更新后的预留
     */
    @Override
    public InventoryReservation releaseReservation(InventoryReservation reservation,
                                                    BigDecimal quantity,
                                                    UUID operatorId) {
        return database(() -> {
            if (reservation.activeQty().compareTo(quantity) < 0) {
                throw new InventoryException(InventoryErrorCode.INV_005, "预留有效数量不足");
            }
            if (reservationMapper.release(toRow(reservation), quantity, operatorId) != 1) {
                throw new InventoryException(InventoryErrorCode.INV_003,
                        "预留版本已变化，请重试");
            }
            BigDecimal releasedQty = reservation.releasedQty().add(quantity);
            String status = releasedQty.compareTo(reservation.reservedQty()) >= 0
                    ? "Released" : "PartiallyReleased";
            return new InventoryReservation(reservation.id(), reservation.tenantId(), reservation.reservationNo(),
                    reservation.sourceType(), reservation.sourceId(), reservation.sourceLineId(),
                    reservation.reservedQty(), releasedQty, status, reservation.version() + 1,
                    reservation.createdAt(), OffsetDateTime.now());
        });
    }

    /**
     * 追加库存流水，不提供修改或删除路径。
     *
     * @param transaction 待追加流水
     * @return 已写入流水
     */
    @Override
    public InventoryTransaction appendTransaction(InventoryTransaction transaction) {
        return database(() -> {
            if (transactionMapper.insert(toRow(transaction)) != 1) {
                throw new ServiceUnavailableException("库存流水追加失败");
            }
            return transaction;
        });
    }

    /**
     * 查询余额分页。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 余额分页
     */
    @Override
    public InventoryBalancePage queryBalances(UUID tenantId, InventoryBalanceQuery query) {
        return database(() -> {
            int offset = (query.page() - 1) * query.size();
            List<InventoryBalance> content = balanceMapper.selectPage(tenantId, query.productId(),
                            query.warehouseId(), query.locationId(), query.normalizedLotNo(),
                            query.size(), offset)
                    .stream().map(this::toBalance).toList();
            return new InventoryBalancePage(content,
                    balanceMapper.count(tenantId, query.productId(), query.warehouseId(),
                            query.locationId(), query.normalizedLotNo()), query.page(), query.size());
        });
    }

    /**
     * 查询预留分页并组装其分配事实。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 预留分页
     */
    @Override
    public InventoryReservationPage queryReservations(UUID tenantId, InventoryReservationQuery query) {
        return database(() -> {
            int offset = (query.page() - 1) * query.size();
            List<InventoryReservationView> content = reservationMapper.selectPage(tenantId,
                            query.reservationId(), query.sourceType(), query.sourceId(), query.sourceLineId(),
                            query.status(), query.productId(), query.warehouseId(), query.locationId(),
                            query.normalizedLotNo(), query.size(), offset)
                    .stream()
                    .map(row -> {
                        InventoryReservation reservation = toReservation(row);
                        List<InventoryReservationAllocation> allocations = allocationMapper
                                .selectByReservationId(tenantId, reservation.id())
                                .stream().map(this::toAllocation).toList();
                        return new InventoryReservationView(reservation, allocations);
                    }).toList();
            long total = reservationMapper.count(tenantId, query.reservationId(), query.sourceType(),
                    query.sourceId(), query.sourceLineId(), query.status(), query.productId(),
                    query.warehouseId(), query.locationId(), query.normalizedLotNo());
            return new InventoryReservationPage(content, total, query.page(), query.size());
        });
    }

    /**
     * 查询追加库存流水分页。
     *
     * @param tenantId 可信租户
     * @param query 查询条件
     * @return 流水分页
     */
    @Override
    public InventoryTransactionPage queryTransactions(UUID tenantId, InventoryTransactionQuery query) {
        return database(() -> {
            int offset = (query.page() - 1) * query.size();
            List<InventoryTransaction> content = transactionMapper.selectPage(tenantId,
                            query.transactionType(), query.sourceType(), query.sourceId(), query.sourceLineId(),
                            query.productId(), query.warehouseId(), query.locationId(), query.normalizedLotNo(),
                            query.occurredFrom(), query.occurredTo(), query.size(), offset)
                    .stream().map(this::toTransaction).toList();
            long total = transactionMapper.count(tenantId, query.transactionType(), query.sourceType(),
                    query.sourceId(), query.sourceLineId(), query.productId(), query.warehouseId(),
                    query.locationId(), query.normalizedLotNo(), query.occurredFrom(), query.occurredTo());
            return new InventoryTransactionPage(content, total, query.page(), query.size());
        });
    }

    /**
     * 聚合当前租户库位的实物与有效预留，供库位停用前置检查复用同一库存事实源。
     * 入参：可信租户和库位 ID；出参：非空库存使用快照；流程：按租户、库位和逻辑删除条件聚合余额，
     * 数据库异常统一转换为库存依赖不可用异常。
     *
     * @param tenantId 可信租户
     * @param locationId 库位 ID
     * @return 库位库存使用快照
     */
    @Override
    public LocationUsageSnapshot queryLocationUsage(UUID tenantId, UUID locationId) {
        return database(() -> {
            InventoryLocationUsageRow row = balanceMapper.selectUsageByLocation(tenantId, locationId);
            if (row == null) {
                throw new ServiceUnavailableException("库位库存使用量聚合结果为空");
            }
            return new LocationUsageSnapshot(row.getOnHandQty(), row.getReservedQty());
        });
    }

    private InventoryBalance toBalance(InventoryBalanceRow row) {
        if (row == null) {
            return null;
        }
        return new InventoryBalance(row.getId(), row.getTenantId(),
                new InventoryDimension(row.getProductId(), row.getWarehouseId(), row.getLocationId(), row.getLotNo()),
                InventoryInvariant.requireNonNegative("onHandQty", row.getOnHandQty()),
                InventoryInvariant.requireNonNegative("reservedQty", row.getReservedQty()),
                row.getVersion() == null ? 0L : row.getVersion(), row.getLastTransactionAt());
    }

    private InventoryBalanceRow toRow(InventoryBalance balance) {
        InventoryBalanceRow row = new InventoryBalanceRow();
        row.setId(balance.id());
        row.setTenantId(balance.tenantId());
        row.setProductId(balance.dimension().productId());
        row.setWarehouseId(balance.dimension().warehouseId());
        row.setLocationId(balance.dimension().locationId());
        row.setLotNo(balance.dimension().lotNo());
        row.setOnHandQty(balance.onHandQty());
        row.setReservedQty(balance.reservedQty());
        row.setVersion(balance.version());
        row.setLastTransactionAt(balance.lastTransactionAt());
        return row;
    }

    private InventoryReservation toReservation(InventoryReservationRow row) {
        if (row == null) {
            return null;
        }
        return new InventoryReservation(row.getId(), row.getTenantId(), row.getReservationNo(),
                row.getSourceType(), row.getSourceId(), row.getSourceLineId(),
                InventoryInvariant.requirePositiveOrZero("reservedQty", row.getReservedQty()),
                InventoryInvariant.requirePositiveOrZero("releasedQty", row.getReleasedQty()), row.getStatus(),
                row.getVersion() == null ? 0L : row.getVersion(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private InventoryReservationRow toRow(InventoryReservation reservation) {
        InventoryReservationRow row = new InventoryReservationRow();
        row.setId(reservation.id());
        row.setTenantId(reservation.tenantId());
        row.setReservationNo(reservation.reservationNo());
        row.setSourceType(reservation.sourceType());
        row.setSourceId(reservation.sourceId());
        row.setSourceLineId(reservation.sourceLineId());
        row.setReservedQty(reservation.reservedQty());
        row.setReleasedQty(reservation.releasedQty());
        row.setStatus(reservation.status());
        row.setVersion(reservation.version());
        row.setCreatedAt(reservation.createdAt());
        row.setUpdatedAt(reservation.updatedAt());
        return row;
    }

    private InventoryReservationAllocation toAllocation(InventoryAllocationRow row) {
        return new InventoryReservationAllocation(row.getId(), row.getTenantId(), row.getReservationId(),
                new InventoryDimension(row.getProductId(), row.getWarehouseId(), row.getLocationId(), row.getLotNo()),
                InventoryInvariant.requirePositiveOrZero("allocatedQty", row.getAllocatedQty()),
                InventoryInvariant.requirePositiveOrZero("releasedQty", row.getReleasedQty()),
                row.getVersion() == null ? 0L : row.getVersion(), row.getCreatedAt(), row.getUpdatedAt());
    }

    private InventoryAllocationRow toRow(InventoryReservationAllocation allocation) {
        InventoryAllocationRow row = new InventoryAllocationRow();
        row.setId(allocation.id());
        row.setTenantId(allocation.tenantId());
        row.setReservationId(allocation.reservationId());
        row.setProductId(allocation.dimension().productId());
        row.setWarehouseId(allocation.dimension().warehouseId());
        row.setLocationId(allocation.dimension().locationId());
        row.setLotNo(allocation.dimension().lotNo());
        row.setAllocatedQty(allocation.allocatedQty());
        row.setReleasedQty(allocation.releasedQty());
        row.setVersion(allocation.version());
        row.setCreatedAt(allocation.createdAt());
        row.setUpdatedAt(allocation.updatedAt());
        return row;
    }

    private InventoryTransactionRow toRow(InventoryTransaction transaction) {
        InventoryTransactionRow row = new InventoryTransactionRow();
        row.setId(transaction.id());
        row.setTenantId(transaction.tenantId());
        row.setTransactionNo(transaction.transactionNo());
        row.setTransactionType(transaction.transactionType());
        row.setSourceType(transaction.sourceType());
        row.setSourceId(transaction.sourceId());
        row.setSourceLineId(transaction.sourceLineId());
        if (transaction.fromDimension() != null) {
            row.setFromProductId(transaction.fromDimension().productId());
            row.setFromWarehouseId(transaction.fromDimension().warehouseId());
            row.setFromLocationId(transaction.fromDimension().locationId());
        }
        row.setFromLotNo(transaction.fromDimension() == null ? "" : transaction.fromDimension().lotNo());
        if (transaction.toDimension() != null) {
            row.setToProductId(transaction.toDimension().productId());
            row.setToWarehouseId(transaction.toDimension().warehouseId());
            row.setToLocationId(transaction.toDimension().locationId());
        }
        row.setToLotNo(transaction.toDimension() == null ? "" : transaction.toDimension().lotNo());
        row.setQuantity(transaction.quantity());
        row.setOccurredAt(transaction.occurredAt());
        row.setOperatorId(transaction.operatorId());
        row.setSessionId(transaction.sessionId());
        row.setRequestId(transaction.requestId());
        row.setIdempotencyKey(transaction.idempotencyKey());
        row.setPayloadDigest(transaction.payloadDigest());
        row.setCreatedAt(OffsetDateTime.now());
        return row;
    }

    private InventoryTransaction toTransaction(InventoryTransactionRow row) {
        InventoryDimension from = row.getFromLocationId() == null ? null
                : new InventoryDimension(row.getFromProductId(), row.getFromWarehouseId(),
                row.getFromLocationId(), row.getFromLotNo());
        InventoryDimension to = row.getToLocationId() == null ? null
                : new InventoryDimension(row.getToProductId(), row.getToWarehouseId(),
                row.getToLocationId(), row.getToLotNo());
        return new InventoryTransaction(row.getId(), row.getTenantId(), row.getTransactionNo(),
                row.getTransactionType(), row.getSourceType(), row.getSourceId(), row.getSourceLineId(),
                from, to, InventoryInvariant.requirePositive("quantity", row.getQuantity()), row.getOccurredAt(),
                row.getOperatorId(), row.getSessionId(), row.getRequestId(), row.getIdempotencyKey(),
                row.getPayloadDigest());
    }

    private <T> T database(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("库存数据库暂时不可用", exception);
        }
    }
}
