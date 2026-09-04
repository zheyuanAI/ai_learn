package com.ailearn.platform.core.manufacturing.operation.infrastructure;

import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionEvent;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionEventType;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionStatus;
import com.ailearn.platform.core.manufacturing.operation.exception.OperationExecutionErrorCode;
import com.ailearn.platform.core.manufacturing.operation.exception.OperationExecutionException;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * 工序执行 PostgreSQL 适配器。
 * <p>
 * 主表保存当前状态快照，事件表保存完整的开始、暂停、恢复和完成时间线；状态更新按租户与版本 CAS，
 * 设备查询只返回同租户事实，避免进程重启或多实例部署后丢失告警生产上下文。
 * </p>
 */
@Repository
public class PostgresOperationExecutionRepository implements OperationExecutionRepository {

    private final DataSource dataSource;

    /** 创建事务感知的工序执行 JDBC 适配器。 */
    public PostgresOperationExecutionRepository(DataSource dataSource) {
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    }

    /** 按租户读取工序执行及其完整事件时间线。 */
    @Override
    public Optional<OperationExecution> find(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findInternal(connection, tenantId, id, false));
            }
        });
    }

    /** 首次保存工序执行；执行编号由服务端生成，不接受客户端伪造。 */
    @Override
    public OperationExecution saveIfAbsent(OperationExecution execution) {
        return database(() -> {
            UUID operatorId = UserContextHolder.requireUserId();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_operation_execution
                             (id, tenant_id, execution_no, dispatch_order_id, work_order_id, operation_id,
                              operator_id, device_id, status, version, created_by, created_at,
                              updated_by, updated_at, isdel)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 0)
                         ON CONFLICT (tenant_id, id) DO NOTHING
                         """)) {
                statement.setObject(1, execution.id());
                statement.setObject(2, execution.tenantId());
                statement.setString(3, "EXEC-" + execution.id());
                statement.setObject(4, execution.dispatchId());
                statement.setObject(5, execution.workOrderId());
                statement.setObject(6, execution.operationId());
                statement.setObject(7, operatorId);
                statement.setObject(8, execution.deviceId());
                statement.setString(9, execution.status().name());
                statement.setLong(10, execution.version());
                statement.setObject(11, operatorId);
                statement.setObject(12, operatorId);
                statement.executeUpdate();
            }
            return find(execution.tenantId(), execution.id())
                    .orElseThrow(() -> new ServiceUnavailableException("工序执行写入后无法读取"));
        });
    }

    /** 在租户与版本范围内原子推进状态并追加新增事件。 */
    @Override
    public OperationExecution update(UUID tenantId, UUID id, UnaryOperator<OperationExecution> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                OperationExecution current = findInternal(connection, tenantId, id, true);
                if (current == null) {
                    return null;
                }
                OperationExecution updated;
                try {
                    updated = updater.apply(current);
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_003,
                            exception.getMessage());
                }
                List<OperationExecutionEvent> newEvents = updated.events().subList(current.events().size(),
                        updated.events().size());
                try (PreparedStatement statement = connection.prepareStatement("""
                         UPDATE mes_operation_execution
                            SET operator_id = ?, status = ?, started_at = ?, paused_at = ?, resumed_at = ?,
                                completed_at = ?, pause_reason = ?, version = ?, updated_by = ?,
                                updated_at = ?
                          WHERE tenant_id = ? AND id = ? AND version = ? AND isdel = 0
                         """)) {
                    statement.setObject(1, latestOperator(updated, current));
                    statement.setString(2, updated.status().name());
                    statement.setObject(3, firstEventAt(updated, OperationExecutionEventType.STARTED));
                    statement.setObject(4, lastEventAt(updated, OperationExecutionEventType.PAUSED));
                    statement.setObject(5, lastEventAt(updated, OperationExecutionEventType.RESUMED));
                    statement.setObject(6, lastEventAt(updated, OperationExecutionEventType.COMPLETED));
                    statement.setString(7, lastPauseReason(updated));
                    statement.setLong(8, updated.version());
                    statement.setObject(9, latestOperator(updated, current));
                    statement.setObject(10, latestTime(updated, current));
                    statement.setObject(11, tenantId);
                    statement.setObject(12, id);
                    statement.setLong(13, current.version());
                    if (statement.executeUpdate() != 1) {
                        throw new OperationExecutionException(OperationExecutionErrorCode.MES_OPERATION_003,
                                "工序执行版本已被其他请求改变");
                    }
                }
                insertEvents(connection, updated, newEvents, current.events().size());
                return findInternal(connection, tenantId, id, false);
            }
        });
    }

    /** 查询同租户设备关联的工序执行，供生产上下文唯一性检查使用。 */
    @Override
    public List<OperationExecution> findByDevice(UUID tenantId, UUID deviceId) {
        return database(() -> {
            List<OperationExecution> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(selectSql()
                         + " WHERE tenant_id = ? AND device_id = ? AND isdel = 0"
                         + " ORDER BY created_at, id")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, deviceId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(read(rows, connection));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    private OperationExecution findInternal(Connection connection, UUID tenantId, UUID id, boolean forUpdate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectSql()
                + " WHERE tenant_id = ? AND id = ? AND isdel = 0"
                + (forUpdate ? " FOR UPDATE" : ""))) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows, connection) : null;
            }
        }
    }

    private OperationExecution read(ResultSet row, Connection connection) throws SQLException {
        return new OperationExecution(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("dispatch_order_id", UUID.class), row.getObject("work_order_id", UUID.class),
                row.getObject("operation_id", UUID.class), row.getObject("device_id", UUID.class),
                OperationExecutionStatus.valueOf(row.getString("status")),
                readEvents(connection, row.getObject("tenant_id", UUID.class), row.getObject("id", UUID.class)),
                row.getLong("version"));
    }

    private List<OperationExecutionEvent> readEvents(Connection connection, UUID tenantId, UUID executionId)
            throws SQLException {
        List<OperationExecutionEvent> events = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event_type, occurred_at, operator_id, reason
                  FROM mes_operation_execution_event
                 WHERE tenant_id = ? AND operation_execution_id = ?
                 ORDER BY event_seq
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, executionId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    events.add(new OperationExecutionEvent(
                            OperationExecutionEventType.valueOf(rows.getString("event_type")),
                            rows.getObject("occurred_at", OffsetDateTime.class),
                            rows.getObject("operator_id", UUID.class), rows.getString("reason")));
                }
            }
        }
        return List.copyOf(events);
    }

    private void insertEvents(Connection connection, OperationExecution execution,
                               List<OperationExecutionEvent> events, int previousCount) throws SQLException {
        if (events.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO mes_operation_execution_event
                    (id, tenant_id, operation_execution_id, event_seq, event_type, occurred_at,
                     operator_id, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """)) {
            for (int i = 0; i < events.size(); i++) {
                OperationExecutionEvent event = events.get(i);
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, execution.tenantId());
                statement.setObject(3, execution.id());
                statement.setInt(4, previousCount + i + 1);
                statement.setString(5, event.type().name());
                statement.setObject(6, event.occurredAt());
                statement.setObject(7, event.operatorId());
                statement.setString(8, event.reason());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private UUID latestOperator(OperationExecution execution, OperationExecution current) {
        return execution.events().isEmpty() ? current.events().isEmpty()
                ? UserContextHolder.requireUserId()
                : current.events().getLast().operatorId() : execution.events().getLast().operatorId();
    }

    private OffsetDateTime latestTime(OperationExecution execution, OperationExecution current) {
        return execution.events().isEmpty() ? current.events().isEmpty()
                ? OffsetDateTime.now() : current.events().getLast().occurredAt()
                : execution.events().getLast().occurredAt();
    }

    private OffsetDateTime firstEventAt(OperationExecution execution, OperationExecutionEventType type) {
        return execution.events().stream().filter(event -> event.type() == type)
                .map(OperationExecutionEvent::occurredAt).findFirst().orElse(null);
    }

    private OffsetDateTime lastEventAt(OperationExecution execution, OperationExecutionEventType type) {
        return execution.events().stream().filter(event -> event.type() == type)
                .map(OperationExecutionEvent::occurredAt).reduce((first, second) -> second).orElse(null);
    }

    private String lastPauseReason(OperationExecution execution) {
        return execution.events().stream().filter(event -> event.type() == OperationExecutionEventType.PAUSED)
                .map(OperationExecutionEvent::reason).reduce((first, second) -> second).orElse(null);
    }

    private String selectSql() {
        return "SELECT id, tenant_id, dispatch_order_id, work_order_id, operation_id, device_id, status, "
                + "version FROM mes_operation_execution";
    }

    private <T> T database(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new ServiceUnavailableException("工序执行数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
