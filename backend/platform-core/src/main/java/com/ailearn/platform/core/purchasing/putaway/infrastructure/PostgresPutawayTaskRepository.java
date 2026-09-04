package com.ailearn.platform.core.purchasing.putaway.infrastructure;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskFact;
import com.ailearn.platform.core.purchasing.putaway.domain.PutawayTaskRepository;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL 12.1 上架任务适配器。
 * <p>
 * 上架任务只记录任务状态和来源，库存位置变化仍由 InventoryCommandService 完成；确认时把实际库存流水
 * 标识回写到 V3 的 inventory_transaction_id，便于审计和重放核对。
 * </p>
 */
@Repository
public class PostgresPutawayTaskRepository implements PutawayTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建事务感知的 JDBC 适配器。
     */
    public PostgresPutawayTaskRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(new TransactionAwareDataSourceProxy(dataSource));
    }

    @Override
    public Optional<UUID> findDefaultStorageLocation(UUID tenantId, UUID warehouseId) {
        return database(() -> jdbcTemplate.query("""
                SELECT id
                  FROM md_location
                 WHERE tenant_id = ? AND warehouse_id = ? AND type = 'Storage'
                   AND status = 'ACTIVE' AND isdel = 0
                 ORDER BY id
                 LIMIT 1
                """, (resultSet, rowNum) -> resultSet.getObject("id", UUID.class), tenantId, warehouseId)
                .stream().findFirst());
    }

    @Override
    public PutawayTaskFact insert(PutawayTaskFact task) {
        return database(() -> {
            jdbcTemplate.update("""
                    INSERT INTO putaway_task
                        (id, tenant_id, task_no, purchase_receipt_id, purchase_receipt_line_id, product_id,
                         from_location_id, to_location_id, putaway_qty, status, inventory_transaction_id,
                         created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, task.id(), task.tenantId(), task.taskNo(), task.purchaseReceiptId(),
                    task.purchaseReceiptLineId(), task.productId(), task.fromLocationId(), task.toLocationId(),
                    task.putawayQty(), task.status(), task.inventoryTransactionId(), task.createdBy(), task.createdAt(),
                    task.createdBy(), task.createdAt());
            return task;
        });
    }

    @Override
    public Optional<PutawayTaskFact> findById(UUID tenantId, UUID taskId, boolean forUpdate) {
        return database(() -> {
            String lock = forUpdate ? " FOR UPDATE" : "";
            return jdbcTemplate.query("""
                    SELECT pt.id, pt.tenant_id, pt.task_no, pt.purchase_receipt_id, pt.purchase_receipt_line_id,
                           pt.product_id, pt.from_location_id, pt.to_location_id, ml.warehouse_id,
                           pt.putaway_qty, pt.status, pt.inventory_transaction_id, pt.confirmed_by, pt.confirmed_at,
                           pt.created_by, pt.created_at
                      FROM putaway_task pt
                      JOIN md_location ml ON ml.tenant_id = pt.tenant_id AND ml.id = pt.from_location_id
                     WHERE pt.tenant_id = ? AND pt.id = ? AND pt.isdel = 0
                    """ + lock, this::readTask, tenantId, taskId).stream().findFirst();
        });
    }

    @Override
    public PutawayTaskFact complete(PutawayTaskFact task, UUID operatorId, OffsetDateTime confirmedAt,
                                    UUID inventoryTransactionId) {
        return database(() -> {
            int updated = jdbcTemplate.update("""
                    UPDATE putaway_task
                       SET status = 'Confirmed', inventory_transaction_id = ?, confirmed_by = ?, confirmed_at = ?,
                           updated_by = ?, updated_at = ?
                     WHERE tenant_id = ? AND id = ? AND status IN ('Pending', 'Processing') AND isdel = 0
                    """, inventoryTransactionId, operatorId, confirmedAt, operatorId, confirmedAt,
                    task.tenantId(), task.id());
            if (updated != 1) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "上架任务状态已变化或不属于当前租户");
            }
            return new PutawayTaskFact(task.id(), task.tenantId(), task.taskNo(), task.purchaseReceiptId(),
                    task.purchaseReceiptLineId(), task.productId(), task.fromLocationId(), task.toLocationId(),
                    task.warehouseId(), task.putawayQty(), "Confirmed", operatorId, confirmedAt, task.createdBy(),
                    task.createdAt(), inventoryTransactionId);
        });
    }

    @Override
    public PutawayTaskFact updateTarget(PutawayTaskFact task, UUID targetLocationId, UUID operatorId) {
        return database(() -> {
            int updated = jdbcTemplate.update("""
                    UPDATE putaway_task
                       SET to_location_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND id = ? AND status IN ('Pending', 'Processing') AND isdel = 0
                    """, targetLocationId, operatorId, task.tenantId(), task.id());
            if (updated != 1) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "上架任务不可修改目标库位");
            }
            return new PutawayTaskFact(task.id(), task.tenantId(), task.taskNo(), task.purchaseReceiptId(),
                    task.purchaseReceiptLineId(), task.productId(), task.fromLocationId(), targetLocationId,
                    task.warehouseId(), task.putawayQty(), task.status(), task.confirmedBy(), task.confirmedAt(),
                    task.createdBy(), task.createdAt(), task.inventoryTransactionId());
        });
    }

    @Override
    public List<PutawayTaskFact> findPage(UUID tenantId, String status, int page, int size) {
        return database(() -> {
            String statusClause = status == null || status.isBlank() ? "" : " AND pt.status = ? ";
            String sql = """
                    SELECT pt.id, pt.tenant_id, pt.task_no, pt.purchase_receipt_id, pt.purchase_receipt_line_id,
                           pt.product_id, pt.from_location_id, pt.to_location_id, ml.warehouse_id,
                           pt.putaway_qty, pt.status, pt.inventory_transaction_id, pt.confirmed_by, pt.confirmed_at,
                           pt.created_by, pt.created_at
                      FROM putaway_task pt
                      JOIN md_location ml ON ml.tenant_id = pt.tenant_id AND ml.id = pt.from_location_id
                     WHERE pt.tenant_id = ? AND pt.isdel = 0
                    """ + statusClause + " ORDER BY pt.created_at DESC, pt.id DESC LIMIT ? OFFSET ?";
            if (status == null || status.isBlank()) {
                return jdbcTemplate.query(sql, this::readTask, tenantId, size, (page - 1) * size);
            }
            return jdbcTemplate.query(sql, this::readTask, tenantId, status, size, (page - 1) * size);
        });
    }

    @Override
    public long count(UUID tenantId, String status) {
        return database(() -> {
            if (status == null || status.isBlank()) {
                return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM putaway_task
                         WHERE tenant_id = ? AND isdel = 0
                        """, Long.class, tenantId);
            }
            return jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM putaway_task
                     WHERE tenant_id = ? AND status = ? AND isdel = 0
                    """, Long.class, tenantId, status);
        });
    }

    private PutawayTaskFact readTask(ResultSet resultSet, int rowNum) throws SQLException {
        return new PutawayTaskFact(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("task_no"), resultSet.getObject("purchase_receipt_id", UUID.class),
                resultSet.getObject("purchase_receipt_line_id", UUID.class), resultSet.getObject("product_id", UUID.class),
                resultSet.getObject("from_location_id", UUID.class), resultSet.getObject("to_location_id", UUID.class),
                resultSet.getObject("warehouse_id", UUID.class), resultSet.getBigDecimal("putaway_qty"),
                resultSet.getString("status"), resultSet.getObject("confirmed_by", UUID.class),
                resultSet.getObject("confirmed_at", OffsetDateTime.class), resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("inventory_transaction_id", UUID.class));
    }

    private <T> T database(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("上架任务数据库暂时不可用", exception);
        }
    }
}
