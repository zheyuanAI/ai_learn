package com.ailearn.platform.core.manufacturing.dispatch.infrastructure;

import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchRepository;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchStatus;
import com.ailearn.platform.core.manufacturing.dispatch.exception.DispatchErrorCode;
import com.ailearn.platform.core.manufacturing.dispatch.exception.DispatchException;
import com.ailearn.platform.core.manufacturing.dispatch.port.DispatchReferencePort;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * 派工安排 PostgreSQL 适配器。
 * <p>
 * 派工只保存安排事实，不推进工单生命周期；状态改变按租户、ID 和版本条件更新，工序执行通过
 * {@link DispatchReferencePort} 读取同一份已持久化安排。
 * </p>
 */
@Repository
public class PostgresDispatchRepository implements DispatchRepository, DispatchReferencePort {

    private final DataSource dataSource;

    /** 创建事务感知的派工 JDBC 适配器。 */
    public PostgresDispatchRepository(DataSource dataSource) {
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    }

    /** 按租户读取派工安排。 */
    @Override
    public Optional<DispatchOrder> find(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findInternal(connection, tenantId, id, false));
            }
        });
    }

    /** 首次保存派工安排；并发重复创建返回同租户同 ID 的已有事实。 */
    @Override
    public DispatchOrder saveIfAbsent(DispatchOrder order) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_dispatch_order
                             (id, tenant_id, dispatch_no, work_order_id, operation_id, operator_id,
                              dispatch_qty, device_id, status, version, created_by, created_at,
                              updated_by, updated_at, isdel)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                         ON CONFLICT (tenant_id, id) DO NOTHING
                         """)) {
                statement.setObject(1, order.id());
                statement.setObject(2, order.tenantId());
                statement.setString(3, "DISP-" + order.id());
                statement.setObject(4, order.workOrderId());
                statement.setObject(5, order.operationId());
                statement.setObject(6, order.operatorId());
                statement.setBigDecimal(7, order.dispatchQty());
                statement.setObject(8, order.deviceId());
                statement.setString(9, order.status().name());
                statement.setLong(10, order.version());
                statement.setObject(11, order.createdBy());
                statement.setObject(12, order.createdAt());
                statement.setObject(13, order.createdBy());
                statement.setObject(14, order.createdAt());
                statement.executeUpdate();
            }
            return find( order.tenantId(), order.id())
                    .orElseThrow(() -> new ServiceUnavailableException("派工写入后无法读取"));
        });
    }

    /** 在租户和版本范围内原子推进派工状态。 */
    @Override
    public DispatchOrder update(UUID tenantId, UUID id, UnaryOperator<DispatchOrder> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                DispatchOrder current = findInternal(connection, tenantId, id, true);
                if (current == null) {
                    return null;
                }
                DispatchOrder updated;
                try {
                    updated = updater.apply(current);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new DispatchException(DispatchErrorCode.MES_DISPATCH_003, exception.getMessage());
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                         UPDATE mes_dispatch_order
                            SET status = ?, released_by = ?, released_at = ?, processing_by = ?,
                                processing_at = ?, completed_by = ?, completed_at = ?, version = ?,
                                updated_by = ?, updated_at = ?
                          WHERE tenant_id = ? AND id = ? AND version = ? AND isdel = 0
                         """)) {
                    statement.setString(1, updated.status().name());
                    statement.setObject(2, updated.releasedBy());
                    statement.setObject(3, updated.releasedAt());
                    statement.setObject(4, updated.processingBy());
                    statement.setObject(5, updated.processingAt());
                    statement.setObject(6, updated.completedBy());
                    statement.setObject(7, updated.completedAt());
                    statement.setLong(8, updated.version());
                    statement.setObject(9, latestOperator(updated, current));
                    statement.setObject(10, latestTime(updated, current));
                    statement.setObject(11, tenantId);
                    statement.setObject(12, id);
                    statement.setLong(13, current.version());
                    if (statement.executeUpdate() != 1) {
                        throw new DispatchException(DispatchErrorCode.MES_DISPATCH_003,
                                "派工版本已被其他请求改变");
                    }
                }
                return findInternal(connection, tenantId, id, false);
            }
        });
    }

    private DispatchOrder findInternal(Connection connection, UUID tenantId, UUID id, boolean forUpdate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectSql()
                + " WHERE tenant_id = ? AND id = ? AND isdel = 0"
                + (forUpdate ? " FOR UPDATE" : ""))) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    private DispatchOrder read(ResultSet row) throws SQLException {
        return new DispatchOrder(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("work_order_id", UUID.class), row.getObject("operation_id", UUID.class),
                row.getObject("operator_id", UUID.class), row.getBigDecimal("dispatch_qty"),
                row.getObject("device_id", UUID.class), DispatchStatus.valueOf(row.getString("status")),
                row.getObject("created_by", UUID.class), row.getObject("created_at", OffsetDateTime.class),
                row.getObject("released_by", UUID.class), row.getObject("released_at", OffsetDateTime.class),
                row.getObject("processing_by", UUID.class), row.getObject("processing_at", OffsetDateTime.class),
                row.getObject("completed_by", UUID.class), row.getObject("completed_at", OffsetDateTime.class),
                row.getLong("version"));
    }

    private UUID latestOperator(DispatchOrder updated, DispatchOrder current) {
        return updated.completedBy() != null ? updated.completedBy()
                : updated.processingBy() != null ? updated.processingBy()
                : updated.releasedBy() != null ? updated.releasedBy() : current.createdBy();
    }

    private OffsetDateTime latestTime(DispatchOrder updated, DispatchOrder current) {
        return updated.completedAt() != null ? updated.completedAt()
                : updated.processingAt() != null ? updated.processingAt()
                : updated.releasedAt() != null ? updated.releasedAt() : current.createdAt();
    }

    private String selectSql() {
        return "SELECT id, tenant_id, work_order_id, operation_id, operator_id, dispatch_qty, device_id, status, created_by, created_at, "
                + "released_by, released_at, processing_by, processing_at, completed_by, completed_at, version "
                + "FROM mes_dispatch_order";
    }

    private <T> T database(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new ServiceUnavailableException("派工数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
