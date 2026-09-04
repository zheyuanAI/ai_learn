package com.ailearn.platform.core.purchasing.infrastructure;

import com.ailearn.platform.core.purchasing.domain.PurchaseCompletionType;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrder;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPage;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderPageQuery;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderRepository;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderStatus;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceipt;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceiptLine;
import com.ailearn.platform.core.purchasing.domain.PurchaseReceiptStatus;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * 采购订单 PostgreSQL 持久化实现。
 * <p>
 * 采用事务感知 JDBC，避免修改现有 Core Mapper 扫描配置。读取订单时可按需加行锁；状态、草稿修改和明细替换
 * 均使用租户条件，状态推进使用版本条件，收货事实与库存应用服务共享外层事务。
 * </p>
 */
@Repository
public class PostgresPurchaseOrderRepository implements PurchaseOrderRepository {

    private final DataSource dataSource;

    /**
     * 创建采购订单 JDBC Repository。
     *
     * @param dataSource Core 数据源
     */
    public PostgresPurchaseOrderRepository(DataSource dataSource) {
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    }

    /**
     * 在调用方事务内写入采购订单和明细。
     */
    @Override
    public PurchaseOrder insert(PurchaseOrder order) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                insertOrder(connection, order);
                insertOrderLines(connection, order);
                return order;
            }
        });
    }

    /**
     * 查询当前租户采购订单。
     */
    @Override
    public Optional<PurchaseOrder> findById(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findInternal(connection, tenantId, id, false));
            }
        });
    }

    /**
     * 查询并锁定当前租户采购订单及明细。
     */
    @Override
    public Optional<PurchaseOrder> findByIdForUpdate(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findInternal(connection, tenantId, id, true));
            }
        });
    }

    /**
     * 以版本条件更新 Draft 采购订单和明细。
     */
    @Override
    public PurchaseOrder updateDraft(PurchaseOrder order, long expectedVersion) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE purchase_order
                           SET supplier_id = ?, expected_arrival_date = ?, remark = ?, version = version + 1,
                               updated_by = ?, updated_at = ?
                         WHERE tenant_id = ? AND id = ? AND status = 'Draft' AND version = ? AND isdel = 0
                        """)) {
                    statement.setObject(1, order.supplierId());
                    statement.setObject(2, order.expectedArrivalDate());
                    statement.setString(3, order.remark());
                    statement.setObject(4, order.updatedBy());
                    statement.setObject(5, order.updatedAt());
                    statement.setObject(6, order.tenantId());
                    statement.setObject(7, order.id());
                    statement.setLong(8, expectedVersion);
                    updated = statement.executeUpdate();
                }
                if (updated != 1) {
                    throw new PurchasingException(PurchasingErrorCode.PO_001,
                            "采购单已被其他请求修改或不再是 Draft");
                }
                replaceDraftLines(connection, order, order.updatedBy());
                return findRequired(connection, order.tenantId(), order.id(), false);
            }
        });
    }

    /**
     * 以版本条件推进采购订单状态或写入完成审计。
     */
    @Override
    public PurchaseOrder updateState(PurchaseOrder order, long expectedVersion) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE purchase_order
                            SET status = ?, completion_type = ?, completion_reason = ?, completed_by = ?,
                                completed_session_id = ?, completed_at = ?, version = version + 1,
                                updated_by = ?, updated_at = ?
                          WHERE tenant_id = ? AND id = ? AND version = ? AND isdel = 0
                         """)) {
                statement.setString(1, order.status().name());
                statement.setString(2, order.completionType() == null ? null : order.completionType().name());
                statement.setString(3, order.completionReason());
                statement.setObject(4, order.completedBy());
                statement.setString(5, order.completedSessionId());
                statement.setObject(6, order.completedAt());
                statement.setObject(7, order.updatedBy());
                statement.setObject(8, order.updatedAt());
                statement.setObject(9, order.tenantId());
                statement.setObject(10, order.id());
                statement.setLong(11, expectedVersion);
                if (statement.executeUpdate() != 1) {
                    throw new PurchasingException(PurchasingErrorCode.PO_001,
                            "采购单状态或版本已被其他请求改变");
                }
                if (order.lines().stream().anyMatch(line -> line.receivedQty().signum() > 0)) {
                    updateReceivedQuantities(connection, order);
                }
                return findRequired(connection, order.tenantId(), order.id(), false);
            }
        });
    }

    /**
     * 在当前事务写入已确认到货验收及明细；收货库存由应用服务在本方法返回后通过库存端口写入。
     */
    @Override
    public PurchaseReceipt insertReceipt(PurchaseReceipt receipt) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_receipt
                            (id, tenant_id, receipt_no, purchase_order_id, receipt_time, quality_hold_location_id,
                             status, confirmed_by, confirmed_session_id, confirmed_at, version,
                             created_by, created_at, updated_by, updated_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    statement.setObject(1, receipt.id());
                    statement.setObject(2, receipt.tenantId());
                    statement.setString(3, receipt.receiptNo());
                    statement.setObject(4, receipt.purchaseOrderId());
                    statement.setObject(5, receipt.receiptTime());
                    statement.setObject(6, receipt.qualityHoldLocationId());
                    statement.setString(7, receipt.status().name());
                    statement.setObject(8, receipt.confirmedBy());
                    statement.setString(9, receipt.confirmedSessionId());
                    statement.setObject(10, receipt.confirmedAt());
                    statement.setLong(11, receipt.version());
                    statement.setObject(12, receipt.createdBy());
                    statement.setObject(13, receipt.createdAt());
                    statement.setObject(14, receipt.updatedBy());
                    statement.setObject(15, receipt.updatedAt());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_receipt_line
                            (id, tenant_id, purchase_receipt_id, purchase_order_line_id, line_no, product_id, uom,
                             arrived_qty, rejected_qty, received_qty, lot_no, rejection_reason,
                             created_by, created_at, updated_by, updated_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    for (PurchaseReceiptLine line : receipt.lines()) {
                        statement.setObject(1, line.id());
                        statement.setObject(2, line.tenantId());
                        statement.setObject(3, receipt.id());
                        statement.setObject(4, line.purchaseOrderLineId());
                        statement.setInt(5, line.lineNo());
                        statement.setObject(6, line.productId());
                        statement.setString(7, line.uom());
                        statement.setBigDecimal(8, line.arrivedQty());
                        statement.setBigDecimal(9, line.rejectedQty());
                        statement.setBigDecimal(10, line.receivedQty());
                        statement.setString(11, line.lotNo());
                        statement.setString(12, line.rejectionReason());
                        statement.setObject(13, receipt.createdBy());
                        statement.setObject(14, receipt.createdAt());
                        statement.setObject(15, receipt.updatedBy());
                        statement.setObject(16, receipt.updatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return receipt;
            }
        });
    }

    /**
     * 查询当前租户采购订单分页；订单明细由同一连接读取，避免跨租户拼接结果。
     */
    @Override
    public PurchaseOrderPage findPage(UUID tenantId, PurchaseOrderPageQuery query) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                StringBuilder where = new StringBuilder(" WHERE tenant_id = ? AND isdel = 0");
                List<Object> whereArgs = new ArrayList<>();
                whereArgs.add(tenantId);
                if (query.keyword() != null) {
                    where.append(" AND po_no ILIKE ?");
                    whereArgs.add("%" + query.keyword() + "%");
                }
                if (query.status() != null) {
                    where.append(" AND status = ?");
                    whereArgs.add(query.status().name());
                }
                long total;
                try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM purchase_order" + where)) {
                    bind(count, whereArgs);
                    try (ResultSet resultSet = count.executeQuery()) {
                        resultSet.next();
                        total = resultSet.getLong(1);
                    }
                }
                List<PurchaseOrder> records = new ArrayList<>();
                String sql = "SELECT id, tenant_id, po_no, supplier_id, expected_arrival_date, status, completion_type, "
                        + "completion_reason, completed_by, completed_session_id, completed_at, remark, version, "
                        + "created_by, created_at, updated_by, updated_at FROM purchase_order" + where
                        + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    List<Object> args = new ArrayList<>(whereArgs);
                    args.add(query.size());
                    args.add((long) (query.page() - 1) * query.size());
                    bind(statement, args);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            records.add(readOrder(resultSet, connection));
                        }
                    }
                }
                return new PurchaseOrderPage(records, total, query.page(), query.size());
            }
        });
    }

    private PurchaseOrder findInternal(Connection connection, UUID tenantId, UUID id, boolean forUpdate)
            throws SQLException {
        if (tenantId == null || id == null) {
            return null;
        }
        String sql = """
                SELECT id, tenant_id, po_no, supplier_id, expected_arrival_date, status, completion_type,
                       completion_reason, completed_by, completed_session_id, completed_at, remark, version,
                       created_by, created_at, updated_by, updated_at
                  FROM purchase_order
                 WHERE tenant_id = ? AND id = ? AND isdel = 0
                """ + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return readOrder(resultSet, connection);
            }
        }
    }

    private PurchaseOrder findRequired(Connection connection, UUID tenantId, UUID id, boolean forUpdate)
            throws SQLException {
        PurchaseOrder order = findInternal(connection, tenantId, id, forUpdate);
        if (order == null) {
            throw new ServiceUnavailableException("采购订单更新后读取失败");
        }
        return order;
    }

    private void insertOrder(Connection connection, PurchaseOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchase_order
                    (id, tenant_id, po_no, supplier_id, expected_arrival_date, status, completion_type,
                     completion_reason, completed_by, completed_session_id, completed_at, remark, version,
                     created_by, created_at, updated_by, updated_at, isdel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            statement.setObject(1, order.id());
            statement.setObject(2, order.tenantId());
            statement.setString(3, order.poNo());
            statement.setObject(4, order.supplierId());
            statement.setObject(5, order.expectedArrivalDate());
            statement.setString(6, order.status().name());
            statement.setString(7, order.completionType() == null ? null : order.completionType().name());
            statement.setString(8, order.completionReason());
            statement.setObject(9, order.completedBy());
            statement.setString(10, order.completedSessionId());
            statement.setObject(11, order.completedAt());
            statement.setString(12, order.remark());
            statement.setLong(13, order.version());
            statement.setObject(14, order.createdBy());
            statement.setObject(15, order.createdAt());
            statement.setObject(16, order.updatedBy());
            statement.setObject(17, order.updatedAt());
            statement.executeUpdate();
        }
    }

    private void insertOrderLines(Connection connection, PurchaseOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchase_order_line
                    (id, tenant_id, purchase_order_id, line_no, product_id, uom, ordered_qty, received_qty,
                     target_warehouse_id, source_work_order_id, created_by, created_at, updated_by, updated_at, isdel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            for (PurchaseOrderLine line : order.lines()) {
                bindOrderLine(statement, order, line);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void replaceDraftLines(Connection connection, PurchaseOrder order, UUID operatorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE purchase_order_line
                   SET isdel = 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND purchase_order_id = ? AND isdel = 0
                """)) {
            statement.setObject(1, operatorId);
            statement.setObject(2, order.tenantId());
            statement.setObject(3, order.id());
            statement.executeUpdate();
        }
        // 逻辑删除保留历史行；新行使用新 UUID，避免主键与旧事实发生冲突。
        List<PurchaseOrderLine> freshLines = order.lines().stream()
                .map(line -> new PurchaseOrderLine(UUID.randomUUID(), line.tenantId(), line.lineNo(), line.productId(),
                        line.uom(), line.orderedQty(), line.receivedQty(), line.targetWarehouseId(), line.sourceWorkOrderId()))
                .toList();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchase_order_line
                    (id, tenant_id, purchase_order_id, line_no, product_id, uom, ordered_qty, received_qty,
                     target_warehouse_id, source_work_order_id, created_by, created_at, updated_by, updated_at, isdel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            for (PurchaseOrderLine line : freshLines) {
                bindOrderLine(statement, order, line);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * 将领域聚合中的累计实际收货量同步到订单明细；调用方已在同一事务内完成库存端口写入。
     *
     * @param connection 当前事务连接
     * @param order 已完成数量累加的采购订单
     * @throws SQLException 数据库写入异常
     */
    private void updateReceivedQuantities(Connection connection, PurchaseOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE purchase_order_line
                   SET received_qty = ?, updated_by = ?, updated_at = ?
                 WHERE tenant_id = ? AND purchase_order_id = ? AND id = ? AND isdel = 0
                """)) {
            for (PurchaseOrderLine line : order.lines()) {
                statement.setBigDecimal(1, line.receivedQty());
                statement.setObject(2, order.updatedBy());
                statement.setObject(3, order.updatedAt());
                statement.setObject(4, order.tenantId());
                statement.setObject(5, order.id());
                statement.setObject(6, line.id());
                if (statement.executeUpdate() != 1) {
                    throw new PurchasingException(PurchasingErrorCode.PO_001,
                            "采购订单明细已被删除或不属于当前采购单");
                }
            }
        }
    }

    private void bindOrderLine(PreparedStatement statement, PurchaseOrder order, PurchaseOrderLine line)
            throws SQLException {
        statement.setObject(1, line.id());
        statement.setObject(2, order.tenantId());
        statement.setObject(3, order.id());
        statement.setInt(4, line.lineNo());
        statement.setObject(5, line.productId());
        statement.setString(6, line.uom());
        statement.setBigDecimal(7, line.orderedQty());
        statement.setBigDecimal(8, line.receivedQty());
        statement.setObject(9, line.targetWarehouseId());
        statement.setObject(10, line.sourceWorkOrderId());
        statement.setObject(11, order.updatedBy());
        statement.setObject(12, order.updatedAt());
        statement.setObject(13, order.updatedBy());
        statement.setObject(14, order.updatedAt());
    }

    private PurchaseOrder readOrder(ResultSet resultSet, Connection connection) throws SQLException {
        UUID tenantId = resultSet.getObject("tenant_id", UUID.class);
        UUID orderId = resultSet.getObject("id", UUID.class);
        List<PurchaseOrderLine> lines = readOrderLines(connection, tenantId, orderId);
        String completionType = resultSet.getString("completion_type");
        return new PurchaseOrder(orderId, tenantId, resultSet.getString("po_no"),
                resultSet.getObject("supplier_id", UUID.class), resultSet.getObject("expected_arrival_date", LocalDate.class),
                PurchaseOrderStatus.parse(resultSet.getString("status")),
                completionType == null ? null : PurchaseCompletionType.valueOf(completionType),
                resultSet.getString("completion_reason"), resultSet.getObject("completed_by", UUID.class),
                resultSet.getString("completed_session_id"), resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getString("remark"), resultSet.getLong("version"), resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class), resultSet.getObject("updated_by", UUID.class),
                resultSet.getObject("updated_at", OffsetDateTime.class), lines);
    }

    private List<PurchaseOrderLine> readOrderLines(Connection connection, UUID tenantId, UUID orderId)
            throws SQLException {
        List<PurchaseOrderLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, tenant_id, purchase_order_id, line_no, product_id, uom, ordered_qty, received_qty,
                       target_warehouse_id, source_work_order_id
                  FROM purchase_order_line
                 WHERE tenant_id = ? AND purchase_order_id = ? AND isdel = 0
                 ORDER BY line_no, id
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new PurchaseOrderLine(resultSet.getObject("id", UUID.class), tenantId,
                            resultSet.getInt("line_no"), resultSet.getObject("product_id", UUID.class),
                            resultSet.getString("uom"), resultSet.getBigDecimal("ordered_qty"),
                            resultSet.getBigDecimal("received_qty"), resultSet.getObject("target_warehouse_id", UUID.class),
                            resultSet.getObject("source_work_order_id", UUID.class)));
                }
            }
        }
        return List.copyOf(lines);
    }

    private void bind(PreparedStatement statement, List<Object> args) throws SQLException {
        for (int index = 0; index < args.size(); index++) {
            statement.setObject(index + 1, args.get(index));
        }
    }

    private <T> T database(SqlSupplier<T> operation) {
        try {
            return operation.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new PurchasingException(PurchasingErrorCode.PO_001,
                        "采购单号、收货单号或业务标识已存在");
            }
            if ("23503".equals(exception.getSQLState()) || "23514".equals(exception.getSQLState())) {
                throw new PurchasingException(PurchasingErrorCode.PO_004,
                        "采购事实引用或数量约束不成立");
            }
            throw new ServiceUnavailableException("采购订单数据库暂时不可用", exception);
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("采购订单数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
