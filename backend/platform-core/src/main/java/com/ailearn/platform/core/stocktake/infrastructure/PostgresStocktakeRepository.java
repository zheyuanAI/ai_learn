package com.ailearn.platform.core.stocktake.infrastructure;

import com.ailearn.platform.core.stocktake.domain.StocktakeLine;
import com.ailearn.platform.core.stocktake.domain.StocktakeOrder;
import com.ailearn.platform.core.stocktake.domain.StocktakeRepository;
import com.ailearn.platform.core.stocktake.domain.StocktakeStatus;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;

/**
 * 盘点 PostgreSQL 持久化实现。
 */
@Repository
public class PostgresStocktakeRepository implements StocktakeRepository {

    private final StocktakeOrderMapper mapper;

    /**
     * 创建盘点持久化实现。
     *
     * @param mapper 盘点 Mapper
     */
    public PostgresStocktakeRepository(StocktakeOrderMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 插入盘点表头。
     * 入参：未盘点聚合；出参：原聚合；流程：只写表头，快照明细在开始盘点时写入。
     */
    @Override
    public StocktakeOrder insert(StocktakeOrder order) {
        return database(() -> {
            if (mapper.insertOrder(toOrderRow(order)) != 1) {
                throw new ServiceUnavailableException("盘点单写入失败");
            }
            return order;
        });
    }

    /**
     * 按租户读取盘点聚合及明细。
     */
    @Override
    public Optional<StocktakeOrder> findById(UUID tenantId, UUID id) {
        return database(() -> {
            StocktakeOrderRow row = mapper.findById(tenantId, id);
            if (row == null) {
                return Optional.empty();
            }
            List<StocktakeLineRow> lineRows = mapper.findLines(tenantId, id);
            return Optional.of(toOrder(row, lineRows == null ? List.of() : lineRows));
        });
    }

    /**
     * 以版本条件推进盘点状态。
     */
    @Override
    public boolean start(UUID tenantId, UUID id, long expectedVersion,
                         UUID operatorId, OffsetDateTime startedAt) {
        return database(() -> mapper.start(tenantId, id, expectedVersion, operatorId, startedAt) == 1);
    }

    /**
     * 批量插入冻结的系统快照明细。
     */
    @Override
    public int insertLines(UUID orderId, List<StocktakeLine> lines, UUID operatorId) {
        return database(() -> {
            int inserted = 0;
            for (StocktakeLine line : lines) {
                if (mapper.insertLine(toLineRow(orderId, line), operatorId) != 1) {
                    throw new ServiceUnavailableException("盘点系统快照明细写入失败");
                }
                inserted++;
            }
            return inserted;
        });
    }

    /**
     * 在同一事务内保存所有实盘结果并推进确认状态。
     */
    @Override
    public boolean confirm(UUID tenantId, UUID id, long expectedVersion, List<StocktakeLine> lines,
                           UUID operatorId, OffsetDateTime confirmedAt) {
        return database(() -> {
            for (StocktakeLine line : lines) {
                if (mapper.updateLine(tenantId, id, toLineRow(line), operatorId) != 1) {
                    throw new ServiceUnavailableException("盘点实盘明细写入失败");
                }
            }
            return mapper.confirm(tenantId, id, expectedVersion, operatorId, confirmedAt) == 1;
        });
    }

    /**
     * 把盘点聚合映射成表头行。
     */
    private StocktakeOrderRow toOrderRow(StocktakeOrder order) {
        StocktakeOrderRow row = new StocktakeOrderRow();
        row.setId(order.id());
        row.setTenantId(order.tenantId());
        row.setStocktakeNo(order.stocktakeNo());
        row.setWarehouseId(order.warehouseId());
        row.setLocationId(order.locationId());
        row.setStatus(order.status().name());
        row.setVersion(order.version());
        row.setStartedBy(order.startedBy());
        row.setStartedAt(order.startedAt());
        row.setConfirmedBy(order.confirmedBy());
        row.setConfirmedAt(order.confirmedAt());
        row.setCreatedBy(order.createdBy());
        row.setCreatedAt(order.createdAt());
        row.setUpdatedBy(order.updatedBy());
        row.setUpdatedAt(order.updatedAt());
        return row;
    }

    /**
     * 把领域明细映射成数据库行。
     */
    private StocktakeLineRow toLineRow(StocktakeLine line) {
        return toLineRow(null, line);
    }

    /**
     * 把领域明细映射成数据库行，并在开始盘点时补入所属盘点单 ID。
     */
    private StocktakeLineRow toLineRow(UUID orderId, StocktakeLine line) {
        StocktakeLineRow row = new StocktakeLineRow();
        row.setId(line.id());
        row.setTenantId(line.tenantId());
        row.setStocktakeOrderId(orderId);
        row.setLineNo(line.lineNo());
        row.setProductId(line.productId());
        row.setWarehouseId(line.warehouseId());
        row.setLocationId(line.locationId());
        row.setLotNo(line.lotNo());
        row.setSystemQty(line.systemQty());
        row.setSystemBalanceVersion(line.systemBalanceVersion());
        row.setCountedQty(line.countedQty());
        row.setVarianceReason(line.varianceReason());
        row.setAdjustmentTransactionId(line.adjustmentTransactionId());
        return row;
    }

    /**
     * 把数据库行重建为领域聚合。
     */
    private StocktakeOrder toOrder(StocktakeOrderRow row, List<StocktakeLineRow> lineRows) {
        List<StocktakeLine> lines = lineRows.stream()
                .map(line -> new StocktakeLine(line.getId(), line.getTenantId(), line.getLineNo(),
                        line.getProductId(), line.getWarehouseId(), line.getLocationId(), line.getLotNo(),
                        line.getSystemQty(), line.getSystemBalanceVersion() == null ? 0 : line.getSystemBalanceVersion(),
                        line.getCountedQty(), line.getVarianceReason(), line.getAdjustmentTransactionId()))
                .toList();
        return new StocktakeOrder(row.getId(), row.getTenantId(), row.getStocktakeNo(), row.getWarehouseId(),
                row.getLocationId(), StocktakeStatus.parse(row.getStatus()), row.getVersion() == null ? 0 : row.getVersion(),
                row.getStartedBy(), row.getStartedAt(), row.getConfirmedBy(), row.getConfirmedAt(),
                row.getCreatedBy(), row.getCreatedAt(), row.getUpdatedBy(), row.getUpdatedAt(), lines);
    }

    /**
     * 统一把非业务数据库异常转换为平台受控异常。
     */
    private <T> T database(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("盘点数据库暂时不可用", exception);
        }
    }
}
