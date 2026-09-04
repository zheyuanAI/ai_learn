package com.ailearn.platform.core.manufacturing.productionfact.infrastructure;

import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceiptStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialDocumentStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssueLine;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturnLine;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspectionStatus;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import com.ailearn.platform.core.manufacturing.productionfact.exception.ProductionFactErrorCode;
import com.ailearn.platform.core.manufacturing.productionfact.exception.ProductionFactException;
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
 * S5 生产事实 PostgreSQL 适配器。
 * <p>
 * 领料、退料、报工、质检和成品入库均显式使用 tenant_id 条件；状态确认在同一事务中采用旧状态条件更新，
 * 生产事实保存与库存应用端口调用共享外层事务。内存仓储仅供 focused tests，不注册为生产 Bean。
 * </p>
 */
@Repository
public class PostgresProductionFactRepository implements ProductionFactRepository {

    private final DataSource dataSource;

    /** 创建事务感知的生产事实 JDBC 适配器。 */
    public PostgresProductionFactRepository(DataSource dataSource) {
        this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
    }

    /** 锁定当前租户工单主事实，串行化退料和成品入库的累计数量检查。 */
    @Override
    public void lockWorkOrder(UUID tenantId, UUID workOrderId) {
        database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT id FROM mes_work_order
                          WHERE tenant_id = ? AND id = ? AND isdel = 0
                          FOR UPDATE
                         """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new ProductionFactException(ProductionFactErrorCode.MES_TENANT_001,
                                "工单不存在或不属于当前租户");
                    }
                }
                return null;
            }
        });
    }

    /** 保存 Draft 领料及其明细。 */
    @Override
    public MaterialIssue saveIssue(MaterialIssue issue) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_material_issue
                            (id, tenant_id, issue_no, work_order_id, status, inventory_operation_id,
                             confirmed_by, confirmed_session_id, confirmed_at, created_by, created_at,
                             updated_by, updated_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    statement.setObject(1, issue.id());
                    statement.setObject(2, issue.tenantId());
                    statement.setString(3, issue.issueNo());
                    statement.setObject(4, issue.workOrderId());
                    statement.setString(5, issue.status().name());
                    statement.setObject(6, issue.inventoryOperationId());
                    statement.setObject(7, issue.confirmedBy());
                    statement.setString(8, issue.confirmedSessionId());
                    statement.setObject(9, issue.confirmedAt());
                    statement.setObject(10, issue.createdBy());
                    statement.setObject(11, issue.createdAt());
                    statement.setObject(12, issue.updatedBy());
                    statement.setObject(13, issue.updatedAt());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_material_issue_line
                            (id, tenant_id, material_issue_id, line_no, product_id, warehouse_id,
                             location_id, issue_qty, inventory_transaction_id, created_by, created_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    for (MaterialIssueLine line : issue.lines()) {
                        statement.setObject(1, line.id());
                        statement.setObject(2, issue.tenantId());
                        statement.setObject(3, issue.id());
                        statement.setInt(4, line.lineNo());
                        statement.setObject(5, line.productId());
                        statement.setObject(6, line.warehouseId());
                        statement.setObject(7, line.locationId());
                        statement.setBigDecimal(8, line.issueQty());
                        statement.setObject(9, line.inventoryTransactionId());
                        statement.setObject(10, issue.createdBy());
                        statement.setObject(11, issue.createdAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return issue;
            }
        });
    }

    /** 按当前租户读取领料。 */
    @Override
    public Optional<MaterialIssue> findIssue(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findIssueInternal(connection, tenantId, id, false));
            }
        });
    }

    /** 以旧状态条件原子更新领料确认事实。 */
    @Override
    public MaterialIssue updateIssue(UUID tenantId, UUID id, UnaryOperator<MaterialIssue> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                MaterialIssue current = findIssueInternal(connection, tenantId, id, true);
                if (current == null) {
                    return null;
                }
                MaterialIssue updated = updater.apply(current);
                if (!updateIssueHeader(connection, current, updated)) {
                    throw stateChanged("领料单状态已被其他请求改变");
                }
                updateIssueLines(connection, updated);
                return findIssueInternal(connection, tenantId, id, false);
            }
        });
    }

    /** 保存 Draft 退料及其明细。 */
    @Override
    public MaterialReturn saveReturn(MaterialReturn value) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_material_return
                            (id, tenant_id, return_no, work_order_id, status, inventory_operation_id,
                             confirmed_by, confirmed_session_id, confirmed_at, created_by, created_at,
                             updated_by, updated_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    statement.setObject(1, value.id());
                    statement.setObject(2, value.tenantId());
                    statement.setString(3, value.returnNo());
                    statement.setObject(4, value.workOrderId());
                    statement.setString(5, value.status().name());
                    statement.setObject(6, value.inventoryOperationId());
                    statement.setObject(7, value.confirmedBy());
                    statement.setString(8, value.confirmedSessionId());
                    statement.setObject(9, value.confirmedAt());
                    statement.setObject(10, value.createdBy());
                    statement.setObject(11, value.createdAt());
                    statement.setObject(12, value.updatedBy());
                    statement.setObject(13, value.updatedAt());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO mes_material_return_line
                            (id, tenant_id, material_return_id, line_no, product_id, warehouse_id,
                             location_id, return_qty, inventory_transaction_id, created_by, created_at, isdel)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """)) {
                    for (MaterialReturnLine line : value.lines()) {
                        statement.setObject(1, line.id());
                        statement.setObject(2, value.tenantId());
                        statement.setObject(3, value.id());
                        statement.setInt(4, line.lineNo());
                        statement.setObject(5, line.productId());
                        statement.setObject(6, line.warehouseId());
                        statement.setObject(7, line.locationId());
                        statement.setBigDecimal(8, line.returnQty());
                        statement.setObject(9, line.inventoryTransactionId());
                        statement.setObject(10, value.createdBy());
                        statement.setObject(11, value.createdAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                return value;
            }
        });
    }

    /** 按当前租户读取退料。 */
    @Override
    public Optional<MaterialReturn> findReturn(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findReturnInternal(connection, tenantId, id, false));
            }
        });
    }

    /** 以旧状态条件原子更新退料确认事实。 */
    @Override
    public MaterialReturn updateReturn(UUID tenantId, UUID id, UnaryOperator<MaterialReturn> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                MaterialReturn current = findReturnInternal(connection, tenantId, id, true);
                if (current == null) {
                    return null;
                }
                MaterialReturn updated = updater.apply(current);
                if (!updateReturnHeader(connection, current, updated)) {
                    throw stateChanged("退料单状态已被其他请求改变");
                }
                updateReturnLines(connection, updated);
                return findReturnInternal(connection, tenantId, id, false);
            }
        });
    }

    /** 保存不可变报工事实。 */
    @Override
    public WorkReport saveReport(WorkReport report) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_work_report
                             (id, tenant_id, report_no, operation_execution_id, work_order_id, operation_id,
                              report_time, qualified_qty, defect_qty, report_qty, remark, created_by, created_at, isdel)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                         """)) {
                statement.setObject(1, report.id());
                statement.setObject(2, report.tenantId());
                statement.setString(3, report.reportNo());
                statement.setObject(4, report.operationExecutionId());
                statement.setObject(5, report.workOrderId());
                statement.setObject(6, report.operationId());
                statement.setObject(7, report.reportTime());
                statement.setBigDecimal(8, report.qualifiedQty());
                statement.setBigDecimal(9, report.defectQty());
                statement.setBigDecimal(10, report.reportQty());
                statement.setString(11, report.remark());
                statement.setObject(12, report.createdBy());
                statement.setObject(13, report.createdAt());
                statement.executeUpdate();
                return report;
            }
        });
    }

    /** 按当前租户读取报工。 */
    @Override
    public Optional<WorkReport> findReport(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(reportSelect()
                         + " WHERE tenant_id = ? AND id = ? AND isdel = 0")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(readReport(resultSet)) : Optional.empty();
                }
            }
        });
    }

    /** 查询当前租户工单报工，按事实时间稳定排序。 */
    @Override
    public List<WorkReport> findReports(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            List<WorkReport> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(reportSelect()
                         + " WHERE tenant_id = ? AND work_order_id = ? AND isdel = 0"
                         + " ORDER BY report_time, id")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(readReport(rows));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    /** 保存 Draft 质检事实。 */
    @Override
    public QualityInspection saveInspection(QualityInspection inspection) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_quality_inspection
                             (id, tenant_id, inspection_no, work_report_id, work_order_id, operation_id,
                              inspection_type, sample_qty, qualified_qty, defect_qty, result, status,
                              submitted_by, submitted_at, created_by, created_at, updated_by, updated_at, isdel)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                         """)) {
                statement.setObject(1, inspection.id());
                statement.setObject(2, inspection.tenantId());
                statement.setString(3, inspection.inspectionNo());
                statement.setObject(4, inspection.workReportId());
                statement.setObject(5, inspection.workOrderId());
                statement.setObject(6, inspection.operationId());
                statement.setString(7, inspection.inspectionType());
                statement.setBigDecimal(8, inspection.sampleQty());
                statement.setBigDecimal(9, inspection.qualifiedQty());
                statement.setBigDecimal(10, inspection.defectQty());
                statement.setString(11, inspection.result());
                statement.setString(12, inspection.status().name());
                statement.setObject(13, inspection.submittedBy());
                statement.setObject(14, inspection.submittedAt());
                statement.setObject(15, inspection.createdBy());
                statement.setObject(16, inspection.createdAt());
                statement.setObject(17, inspection.updatedBy());
                statement.setObject(18, inspection.updatedAt());
                statement.executeUpdate();
                return inspection;
            }
        });
    }

    /** 按当前租户读取质检。 */
    @Override
    public Optional<QualityInspection> findInspection(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(inspectionSelect()
                         + " WHERE tenant_id = ? AND id = ? AND isdel = 0")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(readInspection(resultSet)) : Optional.empty();
                }
            }
        });
    }

    /** 以旧状态条件原子更新质检提交事实。 */
    @Override
    public QualityInspection updateInspection(UUID tenantId, UUID id,
                                              UnaryOperator<QualityInspection> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                QualityInspection current = findInspectionInternal(connection, tenantId, id, true);
                if (current == null) {
                    return null;
                }
                QualityInspection updated = updater.apply(current);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE mes_quality_inspection
                           SET qualified_qty = ?, defect_qty = ?, result = ?, status = ?, submitted_by = ?,
                               submitted_at = ?, updated_by = ?, updated_at = ?
                         WHERE tenant_id = ? AND id = ? AND status = ? AND isdel = 0
                        """)) {
                    statement.setBigDecimal(1, updated.qualifiedQty());
                    statement.setBigDecimal(2, updated.defectQty());
                    statement.setString(3, updated.result());
                    statement.setString(4, updated.status().name());
                    statement.setObject(5, updated.submittedBy());
                    statement.setObject(6, updated.submittedAt());
                    statement.setObject(7, updated.updatedBy());
                    statement.setObject(8, updated.updatedAt());
                    statement.setObject(9, tenantId);
                    statement.setObject(10, id);
                    statement.setString(11, current.status().name());
                    if (statement.executeUpdate() != 1) {
                        throw stateChanged("质检状态已被其他请求改变");
                    }
                }
                return findInspectionInternal(connection, tenantId, id, false);
            }
        });
    }

    /** 查询当前租户工单质检。 */
    @Override
    public List<QualityInspection> findInspections(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            List<QualityInspection> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(inspectionSelect()
                         + " WHERE tenant_id = ? AND work_order_id = ? AND isdel = 0"
                         + " ORDER BY created_at, id")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(readInspection(rows));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    /** 保存 Draft 成品入库。 */
    @Override
    public FinishedGoodsReceipt saveReceipt(FinishedGoodsReceipt receipt) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO mes_finished_goods_receipt
                             (id, tenant_id, receipt_no, work_order_id, receipt_qty, warehouse_id, location_id,
                              status, inventory_operation_id, inventory_transaction_id, confirmed_by,
                              confirmed_session_id, confirmed_at, created_by, created_at, updated_by,
                              updated_at, isdel)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                         """)) {
                statement.setObject(1, receipt.id());
                statement.setObject(2, receipt.tenantId());
                statement.setString(3, receipt.receiptNo());
                statement.setObject(4, receipt.workOrderId());
                statement.setBigDecimal(5, receipt.receiptQty());
                statement.setObject(6, receipt.warehouseId());
                statement.setObject(7, receipt.locationId());
                statement.setString(8, receipt.status().name());
                statement.setObject(9, receipt.inventoryOperationId());
                statement.setObject(10, receipt.inventoryTransactionId());
                statement.setObject(11, receipt.confirmedBy());
                statement.setString(12, receipt.confirmedSessionId());
                statement.setObject(13, receipt.confirmedAt());
                statement.setObject(14, receipt.createdBy());
                statement.setObject(15, receipt.createdAt());
                statement.setObject(16, receipt.updatedBy());
                statement.setObject(17, receipt.updatedAt());
                statement.executeUpdate();
                return receipt;
            }
        });
    }

    /** 按当前租户读取成品入库。 */
    @Override
    public Optional<FinishedGoodsReceipt> findReceipt(UUID tenantId, UUID id) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return Optional.ofNullable(findReceiptInternal(connection, tenantId, id, false));
            }
        });
    }

    /** 以旧状态条件原子更新成品入库确认事实。 */
    @Override
    public FinishedGoodsReceipt updateReceipt(UUID tenantId, UUID id,
                                              UnaryOperator<FinishedGoodsReceipt> updater) {
        return database(() -> {
            try (Connection connection = dataSource.getConnection()) {
                FinishedGoodsReceipt current = findReceiptInternal(connection, tenantId, id, true);
                if (current == null) {
                    return null;
                }
                FinishedGoodsReceipt updated = updater.apply(current);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE mes_finished_goods_receipt
                           SET status = ?, inventory_operation_id = ?, inventory_transaction_id = ?,
                               confirmed_by = ?, confirmed_session_id = ?, confirmed_at = ?,
                               updated_by = ?, updated_at = ?
                         WHERE tenant_id = ? AND id = ? AND status = ? AND isdel = 0
                        """)) {
                    statement.setString(1, updated.status().name());
                    statement.setObject(2, updated.inventoryOperationId());
                    statement.setObject(3, updated.inventoryTransactionId());
                    statement.setObject(4, updated.confirmedBy());
                    statement.setString(5, updated.confirmedSessionId());
                    statement.setObject(6, updated.confirmedAt());
                    statement.setObject(7, updated.updatedBy());
                    statement.setObject(8, updated.updatedAt());
                    statement.setObject(9, tenantId);
                    statement.setObject(10, id);
                    statement.setString(11, current.status().name());
                    if (statement.executeUpdate() != 1) {
                        throw stateChanged("成品入库状态已被其他请求改变");
                    }
                }
                return findReceiptInternal(connection, tenantId, id, false);
            }
        });
    }

    /** 查询当前租户工单成品入库。 */
    @Override
    public List<FinishedGoodsReceipt> findReceipts(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            List<FinishedGoodsReceipt> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(receiptSelect()
                         + " WHERE tenant_id = ? AND work_order_id = ? AND isdel = 0"
                         + " ORDER BY created_at, id")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(readReceipt(rows));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    /** 查询当前租户工单的领料事实。 */
    @Override
    public List<MaterialIssue> findIssues(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            List<MaterialIssue> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(issueSelect()
                         + " WHERE tenant_id = ? AND work_order_id = ? AND isdel = 0"
                         + " ORDER BY created_at, id")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(readIssue(rows, connection));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    /** 查询当前租户工单的退料事实。 */
    @Override
    public List<MaterialReturn> findReturns(UUID tenantId, UUID workOrderId) {
        return database(() -> {
            List<MaterialReturn> result = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(returnSelect()
                         + " WHERE tenant_id = ? AND work_order_id = ? AND isdel = 0"
                         + " ORDER BY created_at, id")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, workOrderId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(readReturn(rows, connection));
                    }
                }
            }
            return List.copyOf(result);
        });
    }

    private MaterialIssue findIssueInternal(Connection connection, UUID tenantId, UUID id, boolean forUpdate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(issueSelect()
                + " WHERE tenant_id = ? AND id = ? AND isdel = 0"
                + (forUpdate ? " FOR UPDATE" : ""))) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readIssue(rows, connection) : null;
            }
        }
    }

    private MaterialReturn findReturnInternal(Connection connection, UUID tenantId, UUID id, boolean forUpdate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(returnSelect()
                + " WHERE tenant_id = ? AND id = ? AND isdel = 0"
                + (forUpdate ? " FOR UPDATE" : ""))) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readReturn(rows, connection) : null;
            }
        }
    }

    private QualityInspection findInspectionInternal(Connection connection, UUID tenantId, UUID id,
                                                      boolean forUpdate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(inspectionSelect()
                + " WHERE tenant_id = ? AND id = ? AND isdel = 0"
                + (forUpdate ? " FOR UPDATE" : ""))) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readInspection(rows) : null;
            }
        }
    }

    private FinishedGoodsReceipt findReceiptInternal(Connection connection, UUID tenantId, UUID id,
                                                     boolean forUpdate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(receiptSelect()
                + " WHERE tenant_id = ? AND id = ? AND isdel = 0"
                + (forUpdate ? " FOR UPDATE" : ""))) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readReceipt(rows) : null;
            }
        }
    }

    private boolean updateIssueHeader(Connection connection, MaterialIssue current, MaterialIssue updated)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mes_material_issue
                   SET status = ?, inventory_operation_id = ?, confirmed_by = ?, confirmed_session_id = ?,
                       confirmed_at = ?, updated_by = ?, updated_at = ?
                 WHERE tenant_id = ? AND id = ? AND status = ? AND isdel = 0
                """)) {
            statement.setString(1, updated.status().name());
            statement.setObject(2, updated.inventoryOperationId());
            statement.setObject(3, updated.confirmedBy());
            statement.setString(4, updated.confirmedSessionId());
            statement.setObject(5, updated.confirmedAt());
            statement.setObject(6, updated.updatedBy());
            statement.setObject(7, updated.updatedAt());
            statement.setObject(8, current.tenantId());
            statement.setObject(9, current.id());
            statement.setString(10, current.status().name());
            return statement.executeUpdate() == 1;
        }
    }

    private void updateIssueLines(Connection connection, MaterialIssue issue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mes_material_issue_line
                   SET inventory_transaction_id = ?
                 WHERE tenant_id = ? AND material_issue_id = ? AND id = ? AND isdel = 0
                """)) {
            for (MaterialIssueLine line : issue.lines()) {
                if (line.inventoryTransactionId() == null) {
                    continue;
                }
                statement.setObject(1, line.inventoryTransactionId());
                statement.setObject(2, issue.tenantId());
                statement.setObject(3, issue.id());
                statement.setObject(4, line.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean updateReturnHeader(Connection connection, MaterialReturn current, MaterialReturn updated)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mes_material_return
                   SET status = ?, inventory_operation_id = ?, confirmed_by = ?, confirmed_session_id = ?,
                       confirmed_at = ?, updated_by = ?, updated_at = ?
                 WHERE tenant_id = ? AND id = ? AND status = ? AND isdel = 0
                """)) {
            statement.setString(1, updated.status().name());
            statement.setObject(2, updated.inventoryOperationId());
            statement.setObject(3, updated.confirmedBy());
            statement.setString(4, updated.confirmedSessionId());
            statement.setObject(5, updated.confirmedAt());
            statement.setObject(6, updated.updatedBy());
            statement.setObject(7, updated.updatedAt());
            statement.setObject(8, current.tenantId());
            statement.setObject(9, current.id());
            statement.setString(10, current.status().name());
            return statement.executeUpdate() == 1;
        }
    }

    private void updateReturnLines(Connection connection, MaterialReturn value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE mes_material_return_line
                   SET inventory_transaction_id = ?
                 WHERE tenant_id = ? AND material_return_id = ? AND id = ? AND isdel = 0
                """)) {
            for (MaterialReturnLine line : value.lines()) {
                if (line.inventoryTransactionId() == null) {
                    continue;
                }
                statement.setObject(1, line.inventoryTransactionId());
                statement.setObject(2, value.tenantId());
                statement.setObject(3, value.id());
                statement.setObject(4, line.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private MaterialIssue readIssue(ResultSet row, Connection connection) throws SQLException {
        return new MaterialIssue(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getString("issue_no"), row.getObject("work_order_id", UUID.class),
                MaterialDocumentStatus.valueOf(row.getString("status")),
                readIssueLines(connection, row.getObject("tenant_id", UUID.class), row.getObject("id", UUID.class)),
                row.getObject("inventory_operation_id", UUID.class), row.getObject("confirmed_by", UUID.class),
                row.getString("confirmed_session_id"), row.getObject("confirmed_at", OffsetDateTime.class),
                row.getObject("created_by", UUID.class), row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_by", UUID.class), row.getObject("updated_at", OffsetDateTime.class));
    }

    private List<MaterialIssueLine> readIssueLines(Connection connection, UUID tenantId, UUID issueId)
            throws SQLException {
        List<MaterialIssueLine> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, line_no, product_id, warehouse_id, location_id, issue_qty, inventory_transaction_id
                  FROM mes_material_issue_line
                 WHERE tenant_id = ? AND material_issue_id = ? AND isdel = 0
                 ORDER BY line_no, id
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, issueId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new MaterialIssueLine(rows.getObject("id", UUID.class), rows.getInt("line_no"),
                            rows.getObject("product_id", UUID.class), rows.getObject("warehouse_id", UUID.class),
                            rows.getObject("location_id", UUID.class), rows.getBigDecimal("issue_qty"),
                            rows.getObject("inventory_transaction_id", UUID.class)));
                }
            }
        }
        return List.copyOf(result);
    }

    private MaterialReturn readReturn(ResultSet row, Connection connection) throws SQLException {
        return new MaterialReturn(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getString("return_no"), row.getObject("work_order_id", UUID.class),
                MaterialDocumentStatus.valueOf(row.getString("status")),
                readReturnLines(connection, row.getObject("tenant_id", UUID.class), row.getObject("id", UUID.class)),
                row.getObject("inventory_operation_id", UUID.class), row.getObject("confirmed_by", UUID.class),
                row.getString("confirmed_session_id"), row.getObject("confirmed_at", OffsetDateTime.class),
                row.getObject("created_by", UUID.class), row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_by", UUID.class), row.getObject("updated_at", OffsetDateTime.class));
    }

    private List<MaterialReturnLine> readReturnLines(Connection connection, UUID tenantId, UUID returnId)
            throws SQLException {
        List<MaterialReturnLine> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, line_no, product_id, warehouse_id, location_id, return_qty, inventory_transaction_id
                  FROM mes_material_return_line
                 WHERE tenant_id = ? AND material_return_id = ? AND isdel = 0
                 ORDER BY line_no, id
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, returnId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new MaterialReturnLine(rows.getObject("id", UUID.class), rows.getInt("line_no"),
                            rows.getObject("product_id", UUID.class), rows.getObject("warehouse_id", UUID.class),
                            rows.getObject("location_id", UUID.class), rows.getBigDecimal("return_qty"),
                            rows.getObject("inventory_transaction_id", UUID.class)));
                }
            }
        }
        return List.copyOf(result);
    }

    private WorkReport readReport(ResultSet row) throws SQLException {
        return new WorkReport(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getString("report_no"), row.getObject("operation_execution_id", UUID.class),
                row.getObject("work_order_id", UUID.class), row.getObject("operation_id", UUID.class),
                row.getObject("report_time", OffsetDateTime.class), row.getBigDecimal("qualified_qty"),
                row.getBigDecimal("defect_qty"), row.getBigDecimal("report_qty"), row.getString("remark"),
                row.getObject("created_by", UUID.class), row.getObject("created_at", OffsetDateTime.class));
    }

    private QualityInspection readInspection(ResultSet row) throws SQLException {
        return new QualityInspection(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getString("inspection_no"), row.getObject("work_report_id", UUID.class),
                row.getObject("work_order_id", UUID.class), row.getObject("operation_id", UUID.class),
                row.getString("inspection_type"), row.getBigDecimal("sample_qty"),
                row.getBigDecimal("qualified_qty"), row.getBigDecimal("defect_qty"), row.getString("result"),
                QualityInspectionStatus.valueOf(row.getString("status")),
                row.getObject("submitted_by", UUID.class), row.getObject("submitted_at", OffsetDateTime.class),
                row.getObject("created_by", UUID.class), row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_by", UUID.class), row.getObject("updated_at", OffsetDateTime.class));
    }

    private FinishedGoodsReceipt readReceipt(ResultSet row) throws SQLException {
        return new FinishedGoodsReceipt(row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getString("receipt_no"), row.getObject("work_order_id", UUID.class),
                row.getBigDecimal("receipt_qty"), row.getObject("warehouse_id", UUID.class),
                row.getObject("location_id", UUID.class), FinishedGoodsReceiptStatus.valueOf(row.getString("status")),
                row.getObject("inventory_operation_id", UUID.class),
                row.getObject("inventory_transaction_id", UUID.class), row.getObject("confirmed_by", UUID.class),
                row.getString("confirmed_session_id"), row.getObject("confirmed_at", OffsetDateTime.class),
                row.getObject("created_by", UUID.class), row.getObject("created_at", OffsetDateTime.class),
                row.getObject("updated_by", UUID.class), row.getObject("updated_at", OffsetDateTime.class));
    }

    private String issueSelect() {
        return "SELECT id, tenant_id, issue_no, work_order_id, status, inventory_operation_id, "
                + "confirmed_by, confirmed_session_id, confirmed_at, created_by, created_at, updated_by, updated_at "
                + "FROM mes_material_issue";
    }

    private String returnSelect() {
        return "SELECT id, tenant_id, return_no, work_order_id, status, inventory_operation_id, "
                + "confirmed_by, confirmed_session_id, confirmed_at, created_by, created_at, updated_by, updated_at "
                + "FROM mes_material_return";
    }

    private String reportSelect() {
        return "SELECT id, tenant_id, report_no, operation_execution_id, work_order_id, operation_id, "
                + "report_time, qualified_qty, defect_qty, report_qty, remark, created_by, created_at "
                + "FROM mes_work_report";
    }

    private String inspectionSelect() {
        return "SELECT id, tenant_id, inspection_no, work_report_id, work_order_id, operation_id, "
                + "inspection_type, sample_qty, qualified_qty, defect_qty, result, status, submitted_by, "
                + "submitted_at, created_by, created_at, updated_by, updated_at FROM mes_quality_inspection";
    }

    private String receiptSelect() {
        return "SELECT id, tenant_id, receipt_no, work_order_id, receipt_qty, warehouse_id, location_id, "
                + "status, inventory_operation_id, inventory_transaction_id, confirmed_by, confirmed_session_id, "
                + "confirmed_at, created_by, created_at, updated_by, updated_at FROM mes_finished_goods_receipt";
    }

    private ProductionFactException stateChanged(String detail) {
        return new ProductionFactException(ProductionFactErrorCode.MES_FACT_001, detail);
    }

    private <T> T database(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new ServiceUnavailableException("生产事实数据库暂时不可用", exception);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
