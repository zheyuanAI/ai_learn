package com.ailearn.platform.core.manufacturing.execution.infrastructure;

import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderCompletionType;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycle;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycleRepository;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderProgress;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * 工单执行生命周期 PostgreSQL 适配器。
 * <p>
 * 生命周期快照与 foundation 工单事实分表保存；写状态时先按租户和工单行锁定快照，再以版本条件更新，
 * 这样重启后仍可恢复审核、执行进度和完成审计，且不会把工序事实伪装成基础工单字段。
 * </p>
 */
@Repository
public class PostgresWorkOrderLifecycleRepository implements WorkOrderLifecycleRepository {

    private final DataSource dataSource;
    private final FoundationRepository foundationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建事务感知的工单生命周期适配器。
     *
     * @param dataSource Core 数据源
     * @param foundationRepository foundation 工单只读事实端口
     * @param objectMapper JSON 序列化器
     */
    public PostgresWorkOrderLifecycleRepository(DataSource dataSource,
                                                FoundationRepository foundationRepository,
                                                ObjectMapper objectMapper) {
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
        this.foundationRepository = foundationRepository;
        this.objectMapper = objectMapper;
    }

    /** 查询当前租户生命周期，并从 foundation 恢复完整工单生产意图。 */
    @Override
    public Optional<WorkOrderLifecycle> find(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            StoredLifecycle stored;
            try (Connection connection = dataSource.getConnection()) {
                stored = select(connection, tenantId, workOrderId, false);
            }
            if (stored == null) {
                return Optional.empty();
            }
            WorkOrderFact workOrder = foundationRepository.findWorkOrder(tenantId, workOrderId)
                    .orElse(null);
            return workOrder == null ? Optional.empty() : Optional.of(toDomain(stored, workOrder));
        });
    }

    /** 首次写入生命周期快照；并发重复创建返回当前租户已存在的快照。 */
    @Override
    public WorkOrderLifecycle saveIfAbsent(WorkOrderLifecycle lifecycle) {
        return database(() -> {
            UUID tenantId = lifecycle.workOrder().tenantId();
            UUID workOrderId = lifecycle.workOrder().id();
            try (Connection connection = dataSource.getConnection()) {
                if (!lockBaseWorkOrder(connection, tenantId, workOrderId)) {
                    throw new ServiceUnavailableException("工单基础事实不存在，无法登记生命周期");
                }
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_work_order_lifecycle
                             (id, tenant_id, work_order_id, status, required_operation_ids,
                              completed_operation_ids, reported_qty, qualified_qty, defect_qty, received_qty,
                              quality_blocked, pending_inventory_commands, locked_bom_version,
                              locked_routing_version, submitted_by, submitted_at, reviewed_by,
                              reviewed_at, rejection_reason, completion_type, completion_reason,
                              completed_by, completed_session_id, completed_at, version,
                              created_at, updated_at, isdel)
                         VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0,
                                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                         ON CONFLICT (tenant_id, work_order_id) DO NOTHING
                         """)) {
                    bind(statement, lifecycle, UUID.randomUUID(), 0L);
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    syncBaseWorkOrderStatus(connection, tenantId, workOrderId, lifecycle.status());
                } else {
                    // 重复登记也修复历史或早期写入造成的主表状态漂移，但以已存在生命周期为准。
                    StoredLifecycle existing = select(connection, tenantId, workOrderId, true);
                    if (existing != null) {
                        syncBaseWorkOrderStatus(connection, tenantId, workOrderId,
                                WorkOrderStatus.valueOf(existing.status()));
                    }
                }
            }
            return find(tenantId, workOrderId)
                    .orElseThrow(() -> new ServiceUnavailableException("工单生命周期写入后无法读取"));
        });
    }

    /** 在基础工单主表上先取得同租户行锁，统一生命周期和生产事实的锁顺序。 */
    private boolean lockBaseWorkOrder(Connection connection, UUID tenantId, UUID workOrderId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                  FROM mes_work_order
                 WHERE tenant_id = ? AND id = ? AND isdel = 0
                 FOR UPDATE
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, workOrderId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** 将生命周期状态与基础工单主表状态在同一数据库事务中双写。 */
    private void syncBaseWorkOrderStatus(Connection connection, UUID tenantId, UUID workOrderId,
                                         WorkOrderStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mes_work_order
                   SET status = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND id = ? AND isdel = 0
                """)) {
            statement.setString(1, status.name());
            statement.setObject(2, tenantId);
            statement.setObject(3, workOrderId);
            if (statement.executeUpdate() != 1) {
                throw new ServiceUnavailableException("工单生命周期与基础工单状态双写失败");
            }
        }
    }

    /** 在租户和工单范围内锁定并按版本更新生命周期快照。 */
    @Override
    public WorkOrderLifecycle update(UUID tenantId, UUID workOrderId,
                                     UnaryOperator<WorkOrderLifecycle> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!lockBaseWorkOrder(connection, tenantId, workOrderId)) {
                    return null;
                }
                StoredLifecycle stored = select(connection, tenantId, workOrderId, true);
                if (stored == null) {
                    return null;
                }
                WorkOrderFact workOrder = foundationRepository.findWorkOrder(tenantId, workOrderId)
                        .orElse(null);
                if (workOrder == null) {
                    return null;
                }
                WorkOrderLifecycle current = toDomain(stored, workOrder);
                WorkOrderLifecycle updated = updater.apply(current);
                long nextVersion = stored.version() + 1;
                try (PreparedStatement statement = connection.prepareStatement("""
                         UPDATE mes_work_order_lifecycle
                            SET status = ?, required_operation_ids = ?::jsonb,
                                completed_operation_ids = ?::jsonb, reported_qty = ?, qualified_qty = ?,
                                defect_qty = ?, received_qty = ?, quality_blocked = ?,
                                pending_inventory_commands = ?, locked_bom_version = ?,
                                locked_routing_version = ?, submitted_by = ?, submitted_at = ?,
                                reviewed_by = ?, reviewed_at = ?, rejection_reason = ?, completion_type = ?,
                                completion_reason = ?, completed_by = ?, completed_session_id = ?,
                                completed_at = ?, version = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE tenant_id = ? AND work_order_id = ? AND version = ? AND isdel = 0
                        """)) {
                    bindUpdate(statement, updated, nextVersion, tenantId, workOrderId, stored.version());
                    if (statement.executeUpdate() != 1) {
                        throw new ServiceUnavailableException("工单生命周期版本已变化");
                    }
                }
                syncBaseWorkOrderStatus(connection, tenantId, workOrderId, updated.status());
                return updated;
            }
        });
    }

    private StoredLifecycle select(Connection connection, UUID tenantId, UUID workOrderId,
                                   boolean forUpdate) throws SQLException {
        String lock = forUpdate ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, tenant_id, work_order_id, status, required_operation_ids,
                       completed_operation_ids, reported_qty, qualified_qty, defect_qty, received_qty,
                       quality_blocked, pending_inventory_commands, locked_bom_version,
                       locked_routing_version, submitted_by, submitted_at, reviewed_by,
                       reviewed_at, rejection_reason, completion_type, completion_reason,
                       completed_by, completed_session_id, completed_at, version
                  FROM mes_work_order_lifecycle
                 WHERE tenant_id = ? AND work_order_id = ? AND isdel = 0
                """ + lock)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, workOrderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? read(resultSet) : null;
            }
        }
    }

    private StoredLifecycle read(ResultSet resultSet) throws SQLException {
        return new StoredLifecycle(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("work_order_id", UUID.class),
                resultSet.getString("status"), resultSet.getString("required_operation_ids"),
                resultSet.getString("completed_operation_ids"), resultSet.getBigDecimal("reported_qty"),
                resultSet.getBigDecimal("qualified_qty"),
                resultSet.getBigDecimal("defect_qty"), resultSet.getBigDecimal("received_qty"),
                resultSet.getBoolean("quality_blocked"),
                resultSet.getBoolean("pending_inventory_commands"),
                resultSet.getString("locked_bom_version"), resultSet.getString("locked_routing_version"),
                resultSet.getObject("submitted_by", UUID.class),
                resultSet.getObject("submitted_at", OffsetDateTime.class),
                resultSet.getObject("reviewed_by", UUID.class),
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getString("rejection_reason"), resultSet.getString("completion_type"),
                resultSet.getString("completion_reason"), resultSet.getObject("completed_by", UUID.class),
                resultSet.getString("completed_session_id"),
                resultSet.getObject("completed_at", OffsetDateTime.class), resultSet.getLong("version"));
    }

    private WorkOrderLifecycle toDomain(StoredLifecycle stored, WorkOrderFact workOrder) {
        Set<UUID> operationIds = parseOperationIds(stored.requiredOperationIdsJson());
        WorkOrderProgress progress = new WorkOrderProgress(parseOperationIds(stored.completedOperationIdsJson()),
                stored.reportedQty(), stored.qualifiedQty(), stored.defectQty(), stored.receivedQty(),
                stored.qualityBlocked(), stored.pendingInventoryCommands());
        WorkOrderStatus status = WorkOrderStatus.valueOf(stored.status());
        return new WorkOrderLifecycle(workOrder.withStatus(status), status, operationIds,
                progress, stored.lockedBomVersion(), stored.lockedRoutingVersion(), stored.submittedBy(),
                stored.submittedAt(), stored.reviewedBy(), stored.reviewedAt(), stored.rejectionReason(),
                stored.completionType() == null ? null : WorkOrderCompletionType.valueOf(stored.completionType()),
                stored.completionReason(), stored.completedBy(), stored.completedSessionId(),
                stored.completedAt());
    }

    private Set<UUID> parseOperationIds(String json) {
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() { });
            Set<UUID> result = new LinkedHashSet<>();
            for (String value : values) {
                result.add(UUID.fromString(value));
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ServiceUnavailableException("工单生命周期工序快照不可解析", exception);
        }
    }

    private String operationIdsJson(Set<UUID> operationIds) {
        try {
            return objectMapper.writeValueAsString(operationIds.stream().map(UUID::toString).sorted().toList());
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("工单生命周期工序快照无法序列化", exception);
        }
    }

    private void bind(PreparedStatement statement, WorkOrderLifecycle lifecycle,
                      UUID id, long version) throws SQLException {
        WorkOrderProgress progress = lifecycle.progress();
        statement.setObject(1, id);
        statement.setObject(2, lifecycle.workOrder().tenantId());
        statement.setObject(3, lifecycle.workOrder().id());
        statement.setString(4, lifecycle.status().name());
        statement.setString(5, operationIdsJson(lifecycle.requiredOperationIds()));
        statement.setString(6, operationIdsJson(progress.completedOperationIds()));
        statement.setBigDecimal(7, progress.reportedQty());
        statement.setBigDecimal(8, progress.qualifiedQty());
        statement.setBigDecimal(9, progress.defectQty());
        statement.setBigDecimal(10, progress.receivedQty());
        statement.setBoolean(11, progress.qualityBlocked());
        statement.setBoolean(12, progress.pendingInventoryCommands());
        statement.setString(13, lifecycle.lockedBomVersion());
        statement.setString(14, lifecycle.lockedRoutingVersion());
        statement.setObject(15, lifecycle.submittedBy());
        statement.setObject(16, lifecycle.submittedAt());
        statement.setObject(17, lifecycle.reviewedBy());
        statement.setObject(18, lifecycle.reviewedAt());
        statement.setString(19, lifecycle.rejectionReason());
        statement.setString(20, lifecycle.completionType() == null ? null : lifecycle.completionType().name());
        statement.setString(21, lifecycle.completionReason());
        statement.setObject(22, lifecycle.completedBy());
        statement.setString(23, lifecycle.completedSessionId());
        statement.setObject(24, lifecycle.completedAt());
    }

    private void bindUpdate(PreparedStatement statement, WorkOrderLifecycle lifecycle, long nextVersion,
                            UUID tenantId, UUID workOrderId, long expectedVersion) throws SQLException {
        WorkOrderProgress progress = lifecycle.progress();
        statement.setString(1, lifecycle.status().name());
        statement.setString(2, operationIdsJson(lifecycle.requiredOperationIds()));
        statement.setString(3, operationIdsJson(progress.completedOperationIds()));
        statement.setBigDecimal(4, progress.reportedQty());
        statement.setBigDecimal(5, progress.qualifiedQty());
        statement.setBigDecimal(6, progress.defectQty());
        statement.setBigDecimal(7, progress.receivedQty());
        statement.setBoolean(8, progress.qualityBlocked());
        statement.setBoolean(9, progress.pendingInventoryCommands());
        statement.setString(10, lifecycle.lockedBomVersion());
        statement.setString(11, lifecycle.lockedRoutingVersion());
        statement.setObject(12, lifecycle.submittedBy());
        statement.setObject(13, lifecycle.submittedAt());
        statement.setObject(14, lifecycle.reviewedBy());
        statement.setObject(15, lifecycle.reviewedAt());
        statement.setString(16, lifecycle.rejectionReason());
        statement.setString(17, lifecycle.completionType() == null ? null : lifecycle.completionType().name());
        statement.setString(18, lifecycle.completionReason());
        statement.setObject(19, lifecycle.completedBy());
        statement.setString(20, lifecycle.completedSessionId());
        statement.setObject(21, lifecycle.completedAt());
        statement.setLong(22, nextVersion);
        statement.setObject(23, tenantId);
        statement.setObject(24, workOrderId);
        statement.setLong(25, expectedVersion);
    }

    private <T> T database(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new ServiceUnavailableException("工单生命周期数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    private record StoredLifecycle(UUID id, UUID tenantId, UUID workOrderId, String status,
                                   String requiredOperationIdsJson, String completedOperationIdsJson,
                                   java.math.BigDecimal reportedQty, java.math.BigDecimal qualifiedQty,
                                   java.math.BigDecimal defectQty, java.math.BigDecimal receivedQty,
                                   boolean qualityBlocked, boolean pendingInventoryCommands,
                                   String lockedBomVersion, String lockedRoutingVersion, UUID submittedBy,
                                   OffsetDateTime submittedAt, UUID reviewedBy, OffsetDateTime reviewedAt,
                                   String rejectionReason, String completionType, String completionReason,
                                   UUID completedBy, String completedSessionId, OffsetDateTime completedAt,
                                   long version) {
    }
}
