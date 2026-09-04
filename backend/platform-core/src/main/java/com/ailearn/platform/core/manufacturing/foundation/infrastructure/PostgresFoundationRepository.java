package com.ailearn.platform.core.manufacturing.foundation.infrastructure;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomComponentFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.BomStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingOperationFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingStatus;
import com.ailearn.platform.core.manufacturing.foundation.domain.SalesLineFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderSourceFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderStatus;
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
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * foundation PostgreSQL 事实适配器。
 * <p>
 * 所有查询和写入均显式绑定租户；BOM、Routing 明细与工单生产意图在调用方事务内保存，
 * 下游只通过同一适配器暴露的只读端口读取来源事实。
 * </p>
 */
@Repository
public class PostgresFoundationRepository implements FoundationRepository {

    private final DataSource dataSource;

    /**
     * 创建事务感知的 foundation JDBC 适配器。
     *
     * @param dataSource Core 数据源
     */
    public PostgresFoundationRepository(DataSource dataSource) {
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    }

    /** 保存 BOM 表头和组件明细。 */
    @Override
    public BomFact saveBom(BomFact bom) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_bom
                            (id, tenant_id, bom_code, product_id, version, status,
                             created_by, created_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    statement.setObject(1, bom.id());
                    statement.setObject(2, bom.tenantId());
                    statement.setString(3, bom.bomCode());
                    statement.setObject(4, bom.productId());
                    statement.setString(5, bom.version());
                    statement.setString(6, bom.status().name());
                    statement.setObject(7, bom.createdBy());
                    statement.setObject(8, bom.createdAt());
                    if (statement.executeUpdate() != 1) {
                        throw new ServiceUnavailableException("BOM 表头写入失败");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_bom_component
                            (id, tenant_id, bom_id, line_no, component_product_id,
                             component_qty, uom, scrap_rate, created_by, created_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    int lineNo = 1;
                    for (BomComponentFact component : bom.components()) {
                        statement.setObject(1, UUID.randomUUID());
                        statement.setObject(2, bom.tenantId());
                        statement.setObject(3, bom.id());
                        statement.setInt(4, lineNo++);
                        statement.setObject(5, component.componentProductId());
                        statement.setBigDecimal(6, component.quantity());
                        statement.setString(7, component.uom());
                        statement.setBigDecimal(8, component.scrapRate());
                        statement.setObject(9, bom.createdBy());
                        statement.setObject(10, bom.createdAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return bom;
            }
        });
    }

    /** 保存 Routing 表头和工序明细。 */
    @Override
    public RoutingFact saveRouting(RoutingFact routing) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_routing
                            (id, tenant_id, routing_code, product_id, version, status,
                             created_by, created_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    statement.setObject(1, routing.id());
                    statement.setObject(2, routing.tenantId());
                    statement.setString(3, routing.routingCode());
                    statement.setObject(4, routing.productId());
                    statement.setString(5, routing.version());
                    statement.setString(6, routing.status().name());
                    statement.setObject(7, routing.createdBy());
                    statement.setObject(8, routing.createdAt());
                    if (statement.executeUpdate() != 1) {
                        throw new ServiceUnavailableException("Routing 表头写入失败");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_routing_operation
                            (id, tenant_id, routing_id, operation_no, operation_name,
                             work_center_id, standard_time_minutes, created_by, created_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    for (RoutingOperationFact operation : routing.operations()) {
                        statement.setObject(1, operation.id());
                        statement.setObject(2, routing.tenantId());
                        statement.setObject(3, routing.id());
                        statement.setInt(4, operation.operationNo());
                        statement.setString(5, operation.operationName());
                        statement.setObject(6, operation.workCenterId());
                        statement.setBigDecimal(7, operation.standardTimeMinutes());
                        statement.setObject(8, routing.createdBy());
                        statement.setObject(9, routing.createdAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return routing;
            }
        });
    }

    /** 保存 Draft 工单生产意图。 */
    @Override
    public WorkOrderFact saveWorkOrder(WorkOrderFact workOrder) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_work_order
                             (id, tenant_id, work_order_no, product_id, planned_qty,
                              planned_start_time, planned_finish_time, bom_id, bom_version,
                              routing_id, routing_version, source_sales_order_line_id,
                              status, version, created_by, created_at, isdel)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0)
                         """)) {
                statement.setObject(1, workOrder.id());
                statement.setObject(2, workOrder.tenantId());
                statement.setString(3, workOrder.workOrderNo());
                statement.setObject(4, workOrder.productId());
                statement.setBigDecimal(5, workOrder.plannedQty());
                statement.setObject(6, workOrder.plannedStartTime());
                statement.setObject(7, workOrder.plannedFinishTime());
                statement.setObject(8, workOrder.bomId());
                statement.setString(9, workOrder.bomVersion());
                statement.setObject(10, workOrder.routingId());
                statement.setString(11, workOrder.routingVersion());
                statement.setObject(12, workOrder.sourceSalesOrderLineId());
                statement.setString(13, workOrder.status().name());
                statement.setObject(14, workOrder.createdBy());
                statement.setObject(15, workOrder.createdAt());
                if (statement.executeUpdate() != 1) {
                    throw new ServiceUnavailableException("工单生产意图写入失败");
                }
                return workOrder;
            }
        });
    }

    /** 按租户读取完整工单生产意图，供执行生命周期查询和下游来源校验复用。 */
    @Override
    public Optional<WorkOrderFact> findWorkOrder(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, tenant_id, work_order_no, product_id, planned_qty,
                                planned_start_time, planned_finish_time, bom_id, bom_version,
                                routing_id, routing_version, source_sales_order_line_id,
                                status, created_by, created_at
                           FROM mes_work_order
                          WHERE tenant_id = ? AND id = ? AND isdel = 0
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(readWorkOrder(resultSet)) : Optional.empty();
                }
            }
        });
    }

    /** 按租户读取未删除工单，供制造 Facts 适配器构建看板摘要。 */
    @Override
    public List<WorkOrderFact> findWorkOrders(UUID tenantId) {
        return database(() -> {
            List<WorkOrderFact> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, tenant_id, work_order_no, product_id, planned_qty,
                                planned_start_time, planned_finish_time, bom_id, bom_version,
                                routing_id, routing_version, source_sales_order_line_id,
                                status, created_by, created_at
                           FROM mes_work_order
                          WHERE tenant_id = ? AND isdel = 0
                          ORDER BY created_at, id
                         """)) {
                statement.setObject(1, tenantId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(readWorkOrder(rows));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    /** 查询当前租户有效 ACTIVE BOM 及组件。 */
    @Override
    public Optional<BomFact> findActiveBom(UUID tenantId, UUID bomId) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, tenant_id, product_id, bom_code, version, status,
                                created_by, created_at
                           FROM mes_bom
                          WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND isdel = 0
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, bomId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readBom(connection, resultSet));
                }
            }
        });
    }

    /** 查询当前租户有效 ACTIVE Routing 及工序。 */
    @Override
    public Optional<RoutingFact> findActiveRouting(UUID tenantId, UUID routingId) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, tenant_id, product_id, routing_code, version, status,
                                created_by, created_at
                           FROM mes_routing
                          WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND isdel = 0
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, routingId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(readRouting(connection, resultSet));
                }
            }
        });
    }

    /** 查询当前租户未删除的销售订单行，销售事实只读且不跨租户拼接。 */
    @Override
    public Optional<SalesLineFact> findActiveLine(UUID tenantId, UUID salesOrderLineId) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT sol.id, sol.tenant_id, sol.product_id, sol.ordered_qty
                           FROM sales_order_line sol
                           JOIN sales_order so ON so.tenant_id = sol.tenant_id
                                                AND so.id = sol.sales_order_id
                          WHERE sol.tenant_id = ? AND sol.id = ?
                            AND sol.isdel = 0 AND so.isdel = 0
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, salesOrderLineId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new SalesLineFact(resultSet.getObject("id", UUID.class),
                            resultSet.getObject("tenant_id", UUID.class),
                            resultSet.getObject("product_id", UUID.class),
                            resultSet.getBigDecimal("ordered_qty"), true));
                }
            }
        });
    }

    /** 查询当前租户未删除工单来源的最小事实。 */
    @Override
    public Optional<WorkOrderSourceFact> findActiveWorkOrder(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id, tenant_id, product_id, work_order_no, planned_qty, status
                           FROM mes_work_order
                          WHERE tenant_id = ? AND id = ? AND isdel = 0
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new WorkOrderSourceFact(resultSet.getObject("id", UUID.class),
                            resultSet.getObject("tenant_id", UUID.class),
                            resultSet.getObject("product_id", UUID.class),
                            resultSet.getString("work_order_no"),
                            resultSet.getBigDecimal("planned_qty"),
                            WorkOrderStatus.valueOf(resultSet.getString("status")), false));
                }
            }
        });
    }

    /** 统计当前租户未删除工单，供健康检查和 focused tests 使用。 */
    @Override
    public long countWorkOrders(UUID tenantId) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT COUNT(*)
                           FROM mes_work_order
                          WHERE tenant_id = ? AND isdel = 0
                         """)) {
                statement.setObject(1, tenantId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        });
    }

    private BomFact readBom(Connection connection, ResultSet resultSet) throws SQLException {
        List<BomComponentFact> components = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT component_product_id, component_qty, uom, scrap_rate
                  FROM mes_bom_component
                 WHERE tenant_id = ? AND bom_id = ? AND isdel = 0
                 ORDER BY line_no, id
                """)) {
            statement.setObject(1, resultSet.getObject("tenant_id", UUID.class));
            statement.setObject(2, resultSet.getObject("id", UUID.class));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    components.add(new BomComponentFact(rows.getObject("component_product_id", UUID.class),
                            rows.getBigDecimal("component_qty"), rows.getString("uom"),
                            rows.getBigDecimal("scrap_rate")));
                }
            }
        }
        return new BomFact(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getObject("product_id", UUID.class),
                resultSet.getString("bom_code"), resultSet.getString("version"),
                BomStatus.valueOf(resultSet.getString("status")), components, false,
                resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private RoutingFact readRouting(Connection connection, ResultSet resultSet) throws SQLException {
        List<RoutingOperationFact> operations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, operation_no, operation_name, work_center_id, standard_time_minutes
                  FROM mes_routing_operation
                 WHERE tenant_id = ? AND routing_id = ? AND isdel = 0
                 ORDER BY operation_no, id
                """)) {
            statement.setObject(1, resultSet.getObject("tenant_id", UUID.class));
            statement.setObject(2, resultSet.getObject("id", UUID.class));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    operations.add(new RoutingOperationFact(rows.getObject("id", UUID.class),
                            rows.getInt("operation_no"), rows.getString("operation_name"),
                            rows.getObject("work_center_id", UUID.class),
                            rows.getBigDecimal("standard_time_minutes")));
                }
            }
        }
        return new RoutingFact(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getObject("product_id", UUID.class),
                resultSet.getString("routing_code"), resultSet.getString("version"),
                RoutingStatus.valueOf(resultSet.getString("status")), operations, false,
                resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private WorkOrderFact readWorkOrder(ResultSet resultSet) throws SQLException {
        return new WorkOrderFact(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getString("work_order_no"),
                resultSet.getObject("product_id", UUID.class), resultSet.getBigDecimal("planned_qty"),
                resultSet.getObject("planned_start_time", OffsetDateTime.class),
                resultSet.getObject("planned_finish_time", OffsetDateTime.class),
                resultSet.getObject("bom_id", UUID.class), resultSet.getString("bom_version"),
                resultSet.getObject("routing_id", UUID.class), resultSet.getString("routing_version"),
                resultSet.getObject("source_sales_order_line_id", UUID.class),
                WorkOrderStatus.valueOf(resultSet.getString("status")), false,
                resultSet.getObject("created_by", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private <T> T database(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new ServiceUnavailableException("制造 foundation 数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
