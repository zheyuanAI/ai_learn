package com.ailearn.platform.core.sales.infrastructure;

import com.ailearn.platform.core.sales.domain.FulfillmentStatus;
import com.ailearn.platform.core.sales.domain.SalesOrder;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import com.ailearn.platform.core.sales.domain.SalesOrderPage;
import com.ailearn.platform.core.sales.domain.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderStatus;
import com.ailearn.platform.core.sales.domain.SalesFulfillmentFact;
import com.ailearn.platform.core.sales.exception.SalesOrderErrorCode;
import com.ailearn.platform.core.sales.exception.SalesOrderException;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.math.BigDecimal;
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
 * 销售订单 PostgreSQL 持久化实现。
 * <p>
 * 使用 JDBC 避免新增未登记的 Mapper 扫描入口；所有 SQL 均显式带 tenant_id，订单状态和草稿修改均以版本条件保护。
 * </p>
 */
@Repository
public class PostgresSalesOrderRepository implements SalesOrderRepository {

    private final DataSource dataSource;

    /**
     * 创建销售订单 JDBC Repository。
     *
     * @param dataSource Core 数据源
     */
    public PostgresSalesOrderRepository(DataSource dataSource) {
        // 事务感知代理确保 try-with-resources 使用的连接仍参与服务层事务，而不是绕过事务管理器新开连接。
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    }

    /**
     * 在调用方事务内写入表头和明细。
     *
     * @param order 销售订单聚合
     * @return 原订单聚合
     */
    @Override
    public SalesOrder insert(SalesOrder order) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO sales_order
                            (id, tenant_id, so_no, customer_id, planned_ship_date, status, completion_type,
                             completion_reason, completed_by, completed_session_id, completed_at, remark, version,
                             created_by, created_at, updated_by, updated_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    bindHeader(statement, order);
                    if (statement.executeUpdate() != 1) {
                        throw new ServiceUnavailableException("销售订单表头写入失败");
                    }
                }
                insertLines(connection, order);
                return order;
            }
        });
    }

    /**
     * 按可信租户查询订单及明细。
     *
     * @param tenantId 可信租户
     * @param id 订单 ID
     * @return 当前租户订单
     */
    @Override
    public Optional<SalesOrder> findById(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, tenant_id, so_no, customer_id, planned_ship_date, status, completion_type,
                                completion_reason, completed_by, completed_session_id, completed_at, remark, version,
                                created_by, created_at, updated_by, updated_at
                           FROM sales_order
                          WHERE tenant_id = ? AND id = ? AND isdel = 0
                          LIMIT 1
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    SalesOrder order = readOrder(resultSet, readLines(connection, tenantId, id));
                    return Optional.of(order);
                }
            }
        });
    }

    /**
     * 以 Draft 和版本条件替换草稿字段及明细。
     *
     * @param order 新草稿聚合
     * @param expectedVersion 读取时版本
     * @return 更新后的订单
     */
    @Override
    public SalesOrder update(SalesOrder order, long expectedVersion) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                int updated;
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sales_order
                           SET customer_id = ?, planned_ship_date = ?, remark = ?, version = ?,
                               updated_by = ?, updated_at = ?
                         WHERE tenant_id = ? AND id = ? AND status = 'Draft' AND version = ? AND isdel = 0
                        """)) {
                    statement.setObject(1, order.customerId());
                    statement.setObject(2, order.plannedShipDate());
                    statement.setString(3, order.remark());
                    statement.setLong(4, order.version());
                    statement.setObject(5, order.updatedBy());
                    statement.setObject(6, order.updatedAt());
                    statement.setObject(7, order.tenantId());
                    statement.setObject(8, order.id());
                    statement.setLong(9, expectedVersion);
                    updated = statement.executeUpdate();
                }
                if (updated != 1) {
                    throw new SalesOrderException(SalesOrderErrorCode.SO_005, "销售订单版本或状态已变化");
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sales_order_line SET isdel = 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                         WHERE tenant_id = ? AND sales_order_id = ? AND isdel = 0
                        """)) {
                    statement.setObject(1, order.updatedBy());
                    statement.setObject(2, order.tenantId());
                    statement.setObject(3, order.id());
                    statement.executeUpdate();
                }
                insertLines(connection, order);
                return order;
            }
        });
    }

    /**
     * 以版本条件推进生命周期或写入完成审计字段。
     *
     * @param order 新状态聚合
     * @param expectedVersion 读取时版本
     * @return 更新后的订单
     */
    @Override
    public SalesOrder updateState(SalesOrder order, long expectedVersion) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         UPDATE sales_order
                            SET status = ?, completion_type = ?, completion_reason = ?, completed_by = ?,
                                completed_session_id = ?, completed_at = ?, version = ?, updated_by = ?, updated_at = ?
                          WHERE tenant_id = ? AND id = ? AND status <> 'Completed' AND version = ? AND isdel = 0
                         """)) {
                statement.setString(1, order.status().name());
                statement.setString(2, order.completionType() == null ? null : order.completionType().name());
                statement.setString(3, order.completionReason());
                statement.setObject(4, order.completedBy());
                statement.setString(5, order.completedSessionId());
                statement.setObject(6, order.completedAt());
                statement.setLong(7, order.version());
                statement.setObject(8, order.updatedBy());
                statement.setObject(9, order.updatedAt());
                statement.setObject(10, order.tenantId());
                statement.setObject(11, order.id());
                statement.setLong(12, expectedVersion);
                if (statement.executeUpdate() != 1) {
                    throw new SalesOrderException(SalesOrderErrorCode.SO_005, "销售订单版本或状态已变化");
                }
                return order;
            }
        });
    }

    /**
     * 在同一事务内更新销售履约数量并追加履约事实。
     * 入参：履约后的订单、旧版本和只追加事实；出参：已保存聚合；流程：先以订单版本条件更新表头，
     * 再更新全部明细并追加事实，任何明细或事实写入失败都会抛错并由上层事务回滚。
     *
     * @param order 履约后的订单聚合
     * @param expectedVersion 读取时订单版本
     * @param facts 本次履约事实
     * @return 已保存订单
     */
    @Override
    public SalesOrder updateFulfillment(SalesOrder order, long expectedVersion,
                                        List<SalesFulfillmentFact> facts) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sales_order
                           SET status = ?, completion_type = ?, completion_reason = ?, completed_by = ?,
                               completed_session_id = ?, completed_at = ?, version = ?, updated_by = ?, updated_at = ?
                         WHERE tenant_id = ? AND id = ? AND status = 'Approved' AND version = ? AND isdel = 0
                        """)) {
                    statement.setString(1, order.status().name());
                    statement.setString(2, order.completionType() == null ? null : order.completionType().name());
                    statement.setString(3, order.completionReason());
                    statement.setObject(4, order.completedBy());
                    statement.setString(5, order.completedSessionId());
                    statement.setObject(6, order.completedAt());
                    statement.setLong(7, order.version());
                    statement.setObject(8, order.updatedBy());
                    statement.setObject(9, order.updatedAt());
                    statement.setObject(10, order.tenantId());
                    statement.setObject(11, order.id());
                    statement.setLong(12, expectedVersion);
                    if (statement.executeUpdate() != 1) {
                        throw new SalesOrderException(SalesOrderErrorCode.SO_005, "销售订单版本或状态已变化");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE sales_order_line
                           SET reserved_qty = ?, picked_qty = ?, shipped_qty = ?, updated_by = ?, updated_at = ?
                         WHERE tenant_id = ? AND sales_order_id = ? AND id = ? AND isdel = 0
                        """)) {
                    for (SalesOrderLine line : order.lines()) {
                        statement.setBigDecimal(1, line.reservedQty());
                        statement.setBigDecimal(2, line.pickedQty());
                        statement.setBigDecimal(3, line.shippedQty());
                        statement.setObject(4, order.updatedBy());
                        statement.setObject(5, order.updatedAt());
                        statement.setObject(6, order.tenantId());
                        statement.setObject(7, order.id());
                        statement.setObject(8, line.id());
                        if (statement.executeUpdate() != 1) {
                            throw new ServiceUnavailableException("销售订单履约明细写入失败");
                        }
                    }
                }
                insertFulfillmentFacts(connection, order, facts);
                return order;
            }
        });
    }

    /**
     * 查询租户订单页；履约状态过滤在应用事实层完成，避免把派生字段伪装成数据库事实。
     *
     * @param tenantId 可信租户
     * @param query 规范化查询条件
     * @return 当前租户订单页
     */
    @Override
    public SalesOrderPage findPage(UUID tenantId, SalesOrderPageQuery query) {
        return database(() -> {
            List<SalesOrder> all = new ArrayList<>();
            StringBuilder sql = new StringBuilder("""
                    SELECT id, tenant_id, so_no, customer_id, planned_ship_date, status, completion_type,
                           completion_reason, completed_by, completed_session_id, completed_at, remark, version,
                           created_by, created_at, updated_by, updated_at
                      FROM sales_order
                     WHERE tenant_id = ? AND isdel = 0
                    """);
            List<Object> args = new ArrayList<>();
            args.add(tenantId);
            if (query.keyword() != null && !query.keyword().isBlank()) {
                sql.append(" AND so_no ILIKE ?");
                args.add("%" + query.keyword().trim() + "%");
            }
            if (query.status() != null) {
                sql.append(" AND status = ?");
                args.add(query.status().name());
            }
            if (query.customerId() != null) {
                sql.append(" AND customer_id = ?");
                args.add(query.customerId());
            }
            sql.append(" ORDER BY created_at DESC, id DESC");
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bindArgs(statement, args);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        UUID id = resultSet.getObject("id", UUID.class);
                        all.add(readOrder(resultSet, readLines(connection, tenantId, id)));
                    }
                }
            }
            List<SalesOrder> filtered = query.fulfillmentStatus() == null ? all : all.stream()
                    .filter(order -> order.fulfillmentStatus() == query.fulfillmentStatus()).toList();
            int from = Math.min((query.page() - 1) * query.size(), filtered.size());
            int to = Math.min(from + query.size(), filtered.size());
            return new SalesOrderPage(filtered.subList(from, to), filtered.size(), query.page(), query.size());
        });
    }

    private void insertLines(Connection connection, SalesOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sales_order_line
                    (id, tenant_id, sales_order_id, line_no, product_id, uom, ordered_qty, reserved_qty,
                     picked_qty, shipped_qty, created_by, created_at, updated_by, updated_at, isdel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            for (SalesOrderLine line : order.lines()) {
                statement.setObject(1, line.id());
                statement.setObject(2, order.tenantId());
                statement.setObject(3, order.id());
                statement.setInt(4, line.lineNo());
                statement.setObject(5, line.productId());
                statement.setString(6, line.uom());
                statement.setBigDecimal(7, line.orderedQty());
                statement.setBigDecimal(8, line.reservedQty());
                statement.setBigDecimal(9, line.pickedQty());
                statement.setBigDecimal(10, line.shippedQty());
                statement.setObject(11, order.updatedBy());
                statement.setObject(12, order.updatedAt());
                statement.setObject(13, order.updatedBy());
                statement.setObject(14, order.updatedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * 追加销售履约事实；该表只记录业务动作，不替代库存流水。
     */
    private void insertFulfillmentFacts(Connection connection, SalesOrder order,
                                        List<SalesFulfillmentFact> facts) throws SQLException {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO sales_fulfillment_fact
                    (id, tenant_id, sales_order_id, sales_order_line_id, action_type, operation_id, quantity,
                     from_location_id, to_location_id, reservation_id, allocation_id, idempotency_key,
                     user_id, session_id, request_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (SalesFulfillmentFact fact : facts) {
                if (fact == null || !order.tenantId().equals(fact.tenantId())
                        || !order.id().equals(fact.salesOrderId())) {
                    throw new ServiceUnavailableException("销售履约事实租户或订单不一致");
                }
                statement.setObject(1, fact.id());
                statement.setObject(2, fact.tenantId());
                statement.setObject(3, fact.salesOrderId());
                statement.setObject(4, fact.salesOrderLineId());
                statement.setString(5, fact.actionType());
                statement.setObject(6, fact.operationId());
                statement.setBigDecimal(7, fact.quantity());
                statement.setObject(8, fact.fromLocationId());
                statement.setObject(9, fact.toLocationId());
                statement.setObject(10, fact.reservationId());
                statement.setObject(11, fact.allocationId());
                statement.setString(12, fact.idempotencyKey());
                statement.setObject(13, fact.userId());
                statement.setString(14, fact.sessionId());
                statement.setString(15, fact.requestId());
                statement.setObject(16, fact.occurredAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<SalesOrderLine> readLines(Connection connection, UUID tenantId, UUID orderId) throws SQLException {
        List<SalesOrderLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, tenant_id, line_no, product_id, uom, ordered_qty, reserved_qty, picked_qty, shipped_qty
                  FROM sales_order_line
                 WHERE tenant_id = ? AND sales_order_id = ? AND isdel = 0
                 ORDER BY line_no, id
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new SalesOrderLine(resultSet.getObject("id", UUID.class),
                            resultSet.getObject("tenant_id", UUID.class), resultSet.getInt("line_no"),
                            resultSet.getObject("product_id", UUID.class), resultSet.getString("uom"),
                            resultSet.getBigDecimal("ordered_qty"), resultSet.getBigDecimal("reserved_qty"),
                            resultSet.getBigDecimal("picked_qty"), resultSet.getBigDecimal("shipped_qty")));
                }
            }
        }
        return List.copyOf(lines);
    }

    private SalesOrder readOrder(ResultSet resultSet, List<SalesOrderLine> lines) throws SQLException {
        return new SalesOrder(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("so_no"), resultSet.getObject("customer_id", UUID.class),
                resultSet.getObject("planned_ship_date", LocalDate.class),
                SalesOrderStatus.parse(resultSet.getString("status")),
                resultSet.getString("completion_type") == null ? null
                        : com.ailearn.platform.core.sales.domain.CompletionType.valueOf(
                                resultSet.getString("completion_type")),
                resultSet.getString("completion_reason"), resultSet.getObject("completed_by", UUID.class),
                resultSet.getString("completed_session_id"), resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getString("remark"), resultSet.getLong("version"),
                resultSet.getObject("created_by", UUID.class), resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_by", UUID.class), resultSet.getObject("updated_at", OffsetDateTime.class),
                lines);
    }

    private void bindHeader(PreparedStatement statement, SalesOrder order) throws SQLException {
        statement.setObject(1, order.id());
        statement.setObject(2, order.tenantId());
        statement.setString(3, order.soNo());
        statement.setObject(4, order.customerId());
        statement.setObject(5, order.plannedShipDate());
        statement.setString(6, order.status().name());
        statement.setString(7, null);
        statement.setString(8, null);
        statement.setObject(9, null);
        statement.setString(10, null);
        statement.setObject(11, null);
        statement.setString(12, order.remark());
        statement.setLong(13, order.version());
        statement.setObject(14, order.createdBy());
        statement.setObject(15, order.createdAt());
        statement.setObject(16, order.updatedBy());
        statement.setObject(17, order.updatedAt());
    }

    private void bindArgs(PreparedStatement statement, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            statement.setObject(i + 1, args.get(i));
        }
    }

    private <T> T database(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new ServiceUnavailableException("销售订单数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
