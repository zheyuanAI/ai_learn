package com.ailearn.platform.core.quality.infrastructure;

import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.quality.domain.QualityDispositionFact;
import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import com.ailearn.platform.core.quality.domain.QualityInspectionFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptFact;
import com.ailearn.platform.core.quality.domain.QualityReceiptLineFact;
import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL 12.1 质量事实适配器。
 * <p>
 * 该适配器只访问 V3 中的采购收货、质检和处置表；库存表永远通过 InventoryCommandService 写入。
 * 所有 SQL 都显式绑定 tenant_id，并在状态变更前锁定当前事实行。
 * </p>
 */
@Repository
public class PostgresPurchaseQualityRepository implements PurchaseQualityRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建事务感知的 JDBC 适配器。
     */
    public PostgresPurchaseQualityRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(new TransactionAwareDataSourceProxy(dataSource));
    }

    @Override
    public Optional<QualityReceiptFact> findReceipt(UUID tenantId, UUID receiptId, boolean forUpdate) {
        return database(() -> {
            String lock = forUpdate ? " FOR UPDATE" : "";
            List<QualityReceiptFact> rows = jdbcTemplate.query("""
                    SELECT pr.id, pr.tenant_id, pr.purchase_order_id, po.po_no, pr.receipt_time,
                           pr.quality_hold_location_id, pr.status,
                           prl.id AS line_id, prl.purchase_order_line_id, prl.product_id, prl.uom,
                           prl.received_qty, prl.lot_no, pol.target_warehouse_id
                      FROM purchase_receipt pr
                      JOIN purchase_order po ON po.tenant_id = pr.tenant_id AND po.id = pr.purchase_order_id
                      JOIN purchase_receipt_line prl ON prl.tenant_id = pr.tenant_id
                           AND prl.purchase_receipt_id = pr.id AND prl.isdel = 0
                      JOIN purchase_order_line pol ON pol.tenant_id = prl.tenant_id
                           AND pol.id = prl.purchase_order_line_id AND pol.isdel = 0
                     WHERE pr.tenant_id = ? AND pr.id = ? AND pr.isdel = 0 AND po.isdel = 0
                     ORDER BY prl.line_no, prl.id
                    """ + lock, this::readReceiptRow, tenantId, receiptId);
            if (rows.isEmpty()) return Optional.empty();
            QualityReceiptFact first = rows.get(0);
            return Optional.of(new QualityReceiptFact(first.id(), first.tenantId(), first.purchaseOrderId(),
                    first.purchaseOrderNo(), first.receiptTime(), first.qualityHoldLocationId(), first.status(),
                    rows.stream().flatMap(row -> row.lines().stream()).toList()));
        });
    }

    @Override
    public Optional<QualityInspectionFact> findInspection(UUID tenantId, UUID inspectionId, boolean forUpdate) {
        return database(() -> findInspectionInternal(tenantId, inspectionId, forUpdate));
    }

    @Override
    public List<QualityInspectionFact> findInspectionsByLine(UUID tenantId, UUID receiptLineId, boolean forUpdate) {
        return database(() -> {
            String lock = forUpdate ? " FOR UPDATE" : "";
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, purchase_receipt_id, purchase_receipt_line_id, product_id,
                           inspected_qty, qualified_qty, unqualified_qty, inspection_note, status,
                           inspected_by, inspected_at, created_at
                      FROM purchase_quality_inspection
                     WHERE tenant_id = ? AND purchase_receipt_line_id = ? AND isdel = 0
                     ORDER BY created_at, id
                    """ + lock, this::readInspection, tenantId, receiptLineId);
        });
    }

    @Override
    public QualityInspectionFact insertInspection(QualityInspectionFact inspection) {
        return database(() -> {
            jdbcTemplate.update("""
                    INSERT INTO purchase_quality_inspection
                        (id, tenant_id, purchase_receipt_id, purchase_receipt_line_id, product_id,
                         inspected_qty, qualified_qty, unqualified_qty, inspection_note, status,
                         inspected_by, inspected_at, created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, inspection.id(), inspection.tenantId(), inspection.purchaseReceiptId(),
                    inspection.purchaseReceiptLineId(), inspection.productId(), inspection.inspectedQty(),
                    inspection.qualifiedQty(), inspection.unqualifiedQty(), inspection.inspectionNote(),
                    inspection.status(), inspection.inspectedBy(), inspection.inspectedAt(), inspection.inspectedBy(),
                    inspection.createdAt(), inspection.inspectedBy(), inspection.createdAt());
            return inspection;
        });
    }

    @Override
    public List<QualityDispositionFact> findDispositionsByInspection(UUID tenantId, UUID inspectionId,
                                                                      boolean forUpdate) {
        return database(() -> {
            String lock = forUpdate ? " FOR UPDATE" : "";
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, purchase_quality_inspection_id, disposition_type,
                           disposition_qty, reason, status, decided_by, decided_at, executed_by,
                           executed_at, inventory_transaction_id, created_at
                      FROM purchase_quality_disposition
                     WHERE tenant_id = ? AND purchase_quality_inspection_id = ? AND isdel = 0
                     ORDER BY created_at, id
                    """ + lock, this::readDisposition, tenantId, inspectionId);
        });
    }

    @Override
    public Optional<QualityDispositionFact> findDisposition(UUID tenantId, UUID dispositionId, boolean forUpdate) {
        return database(() -> {
            String lock = forUpdate ? " FOR UPDATE" : "";
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, purchase_quality_inspection_id, disposition_type,
                           disposition_qty, reason, status, decided_by, decided_at, executed_by,
                           executed_at, inventory_transaction_id, created_at
                      FROM purchase_quality_disposition
                     WHERE tenant_id = ? AND id = ? AND isdel = 0
                    """ + lock, this::readDisposition, tenantId, dispositionId).stream().findFirst();
        });
    }

    @Override
    public QualityDispositionFact insertDisposition(QualityDispositionFact disposition) {
        return database(() -> {
            jdbcTemplate.update("""
                    INSERT INTO purchase_quality_disposition
                        (id, tenant_id, purchase_quality_inspection_id, disposition_type, disposition_qty,
                         reason, status, decided_by, decided_at, created_by, created_at, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, disposition.id(), disposition.tenantId(), disposition.inspectionId(),
                    disposition.type().name(), disposition.quantity(), disposition.reason(), disposition.status(),
                    disposition.decidedBy(), disposition.decidedAt(), disposition.decidedBy(), disposition.createdAt(),
                    disposition.decidedBy(), disposition.createdAt());
            return disposition;
        });
    }

    @Override
    public QualityDispositionFact completeDisposition(QualityDispositionFact disposition, UUID operatorId,
                                                      OffsetDateTime executedAt, UUID inventoryTransactionId) {
        return database(() -> {
            int updated = jdbcTemplate.update("""
                    UPDATE purchase_quality_disposition
                       SET status = 'Completed', executed_by = ?, executed_at = ?,
                           inventory_transaction_id = ?, updated_by = ?, updated_at = ?
                     WHERE tenant_id = ? AND id = ? AND status = 'PendingExecution' AND isdel = 0
                    """, operatorId, executedAt, inventoryTransactionId, operatorId, executedAt,
                    disposition.tenantId(), disposition.id());
            if (updated != 1) {
                throw new PurchasingException(PurchasingErrorCode.PO_006, "质量处置状态已变化或不属于当前租户");
            }
            return new QualityDispositionFact(disposition.id(), disposition.tenantId(), disposition.inspectionId(),
                    disposition.type(), disposition.quantity(), disposition.reason(), "Completed",
                    disposition.decidedBy(), disposition.decidedAt(), operatorId, executedAt,
                    inventoryTransactionId, disposition.createdAt());
        });
    }

    @Override
    public List<QualityInspectionFact> listInspections(UUID tenantId) {
        return database(() -> jdbcTemplate.query("""
                SELECT id, tenant_id, purchase_receipt_id, purchase_receipt_line_id, product_id,
                       inspected_qty, qualified_qty, unqualified_qty, inspection_note, status,
                       inspected_by, inspected_at, created_at
                  FROM purchase_quality_inspection
                 WHERE tenant_id = ? AND isdel = 0
                 ORDER BY created_at DESC, id DESC
                """, this::readInspection, tenantId));
    }

    @Override
    public List<QualityDispositionFact> listDispositions(UUID tenantId) {
        return database(() -> jdbcTemplate.query("""
                SELECT id, tenant_id, purchase_quality_inspection_id, disposition_type,
                       disposition_qty, reason, status, decided_by, decided_at, executed_by,
                       executed_at, inventory_transaction_id, created_at
                  FROM purchase_quality_disposition
                 WHERE tenant_id = ? AND isdel = 0
                 ORDER BY created_at DESC, id DESC
                """, this::readDisposition, tenantId));
    }

    private Optional<QualityInspectionFact> findInspectionInternal(UUID tenantId, UUID inspectionId,
                                                                    boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        return jdbcTemplate.query("""
                SELECT id, tenant_id, purchase_receipt_id, purchase_receipt_line_id, product_id,
                       inspected_qty, qualified_qty, unqualified_qty, inspection_note, status,
                       inspected_by, inspected_at, created_at
                  FROM purchase_quality_inspection
                 WHERE tenant_id = ? AND id = ? AND isdel = 0
                """ + lock, this::readInspection, tenantId, inspectionId).stream().findFirst();
    }

    private QualityReceiptFact readReceiptRow(ResultSet resultSet, int rowNum) throws SQLException {
        QualityReceiptLineFact line = new QualityReceiptLineFact(
                resultSet.getObject("line_id", UUID.class),
                resultSet.getObject("purchase_order_line_id", UUID.class),
                resultSet.getObject("product_id", UUID.class), resultSet.getString("uom"),
                resultSet.getBigDecimal("received_qty"), resultSet.getString("lot_no"),
                resultSet.getObject("target_warehouse_id", UUID.class));
        return new QualityReceiptFact(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getObject("purchase_order_id", UUID.class),
                resultSet.getString("po_no"), resultSet.getObject("receipt_time", OffsetDateTime.class),
                resultSet.getObject("quality_hold_location_id", UUID.class), resultSet.getString("status"),
                List.of(line));
    }

    private QualityInspectionFact readInspection(ResultSet resultSet, int rowNum) throws SQLException {
        return new QualityInspectionFact(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class), resultSet.getObject("purchase_receipt_id", UUID.class),
                resultSet.getObject("purchase_receipt_line_id", UUID.class),
                resultSet.getObject("product_id", UUID.class), resultSet.getBigDecimal("inspected_qty"),
                resultSet.getBigDecimal("qualified_qty"), resultSet.getBigDecimal("unqualified_qty"),
                resultSet.getString("inspection_note"), resultSet.getString("status"),
                resultSet.getObject("inspected_by", UUID.class), resultSet.getObject("inspected_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private QualityDispositionFact readDisposition(ResultSet resultSet, int rowNum) throws SQLException {
        return new QualityDispositionFact(resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("purchase_quality_inspection_id", UUID.class),
                QualityDispositionType.valueOf(resultSet.getString("disposition_type")),
                resultSet.getBigDecimal("disposition_qty"), resultSet.getString("reason"),
                resultSet.getString("status"), resultSet.getObject("decided_by", UUID.class),
                resultSet.getObject("decided_at", OffsetDateTime.class), resultSet.getObject("executed_by", UUID.class),
                resultSet.getObject("executed_at", OffsetDateTime.class),
                resultSet.getObject("inventory_transaction_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private <T> T database(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("采购质量事实数据库暂时不可用", exception);
        }
    }
}
