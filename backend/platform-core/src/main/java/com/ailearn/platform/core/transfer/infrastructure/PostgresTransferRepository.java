package com.ailearn.platform.core.transfer.infrastructure;

import com.ailearn.platform.core.transfer.domain.TransferLine;
import com.ailearn.platform.core.transfer.domain.TransferOrder;
import com.ailearn.platform.core.transfer.domain.TransferRepository;
import com.ailearn.platform.core.transfer.domain.TransferStatus;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;

/**
 * 调拨 PostgreSQL 持久化实现。
 */
@Repository
public class PostgresTransferRepository implements TransferRepository {

    private final TransferOrderMapper mapper;

    /**
     * 创建调拨持久化实现。
     *
     * @param mapper 调拨 Mapper
     */
    public PostgresTransferRepository(TransferOrderMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在当前事务内插入表头和明细。
     * 入参：已校验的调拨聚合；出参：原聚合；流程：先写表头，再写所有明细，任一失败交由事务回滚。
     *
     * @param order 调拨聚合
     * @return 已持久化调拨聚合
     */
    @Override
    public TransferOrder insert(TransferOrder order) {
        return database(() -> {
            if (mapper.insertOrder(toRow(order)) != 1) {
                throw new ServiceUnavailableException("调拨单表头写入失败");
            }
            for (TransferLine line : order.lines()) {
                if (mapper.insertLine(toRow(order, line), order.createdBy()) != 1) {
                    throw new ServiceUnavailableException("调拨单明细写入失败");
                }
            }
            return order;
        });
    }

    /**
     * 按租户读取调拨聚合。
     *
     * @param tenantId 可信租户
     * @param id 调拨单 ID
     * @return 调拨聚合，可为空
     */
    @Override
    public Optional<TransferOrder> findById(UUID tenantId, UUID id) {
        return database(() -> {
            TransferOrderRow row = mapper.findById(tenantId, id);
            if (row == null) {
                return Optional.empty();
            }
            return Optional.of(toOrder(row, mapper.findLines(tenantId, id)));
        });
    }

    /**
     * 以版本条件推进调拨状态。
     *
     * @param tenantId 可信租户
     * @param id 调拨单 ID
     * @param expectedVersion 期望版本
     * @param operatorId 可信操作人
     * @param confirmedAt 确认时间
     * @return 是否成功更新一行
     */
    @Override
    public boolean confirm(UUID tenantId, UUID id, long expectedVersion,
                           UUID operatorId, OffsetDateTime confirmedAt) {
        return database(() -> mapper.confirm(tenantId, id, expectedVersion, operatorId, confirmedAt) == 1);
    }

    /**
     * 把领域聚合映射成表头行。
     */
    private TransferOrderRow toRow(TransferOrder order) {
        TransferOrderRow row = new TransferOrderRow();
        row.setId(order.id());
        row.setTenantId(order.tenantId());
        row.setTransferNo(order.transferNo());
        row.setFromWarehouseId(order.fromWarehouseId());
        row.setFromLocationId(order.fromLocationId());
        row.setToWarehouseId(order.toWarehouseId());
        row.setToLocationId(order.toLocationId());
        row.setStatus(order.status().name());
        row.setVersion(order.version());
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
    private TransferLineRow toRow(TransferOrder order, TransferLine line) {
        TransferLineRow row = new TransferLineRow();
        row.setId(line.id());
        row.setTenantId(line.tenantId());
        row.setTransferOrderId(order.id());
        row.setLineNo(line.lineNo());
        row.setProductId(line.productId());
        row.setLotNo(line.lotNo());
        row.setUom(line.uom());
        row.setQuantity(line.quantity());
        return row;
    }

    /**
     * 把数据库行重建为不可变领域聚合。
     */
    private TransferOrder toOrder(TransferOrderRow row, List<TransferLineRow> lineRows) {
        List<TransferLine> lines = lineRows.stream()
                .map(line -> new TransferLine(line.getId(), line.getTenantId(), line.getLineNo(),
                        line.getProductId(), line.getLotNo(), line.getUom(), line.getQuantity()))
                .toList();
        return new TransferOrder(row.getId(), row.getTenantId(), row.getTransferNo(), row.getFromWarehouseId(),
                row.getFromLocationId(), row.getToWarehouseId(), row.getToLocationId(),
                TransferStatus.parse(row.getStatus()), row.getVersion() == null ? 0 : row.getVersion(),
                row.getConfirmedBy(), row.getConfirmedAt(), row.getCreatedBy(), row.getCreatedAt(),
                row.getUpdatedBy(), row.getUpdatedAt(), lines);
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
            throw new ServiceUnavailableException("调拨数据库暂时不可用", exception);
        }
    }
}
