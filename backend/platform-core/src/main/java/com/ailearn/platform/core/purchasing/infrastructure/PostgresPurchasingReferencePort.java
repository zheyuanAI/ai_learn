package com.ailearn.platform.core.purchasing.infrastructure;

import com.ailearn.platform.core.purchasing.domain.PurchasingLocationFact;
import com.ailearn.platform.core.purchasing.domain.PurchasingProductFact;
import com.ailearn.platform.core.purchasing.domain.port.PurchasingReferencePort;
import com.ailearn.platform.shared.exception.BaseException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Repository;

/**
 * 采购主数据只读适配器。
 * <p>
 * Core 当前 Mapper 扫描清单不包含采购包；本适配器使用事务感知 JDBC 查询主数据，所有查询都绑定可信租户、
 * ACTIVE 状态和逻辑删除条件。它不写任何主数据或库存表。
 * </p>
 */
@Repository
public class PostgresPurchasingReferencePort implements PurchasingReferencePort {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建采购主数据引用适配器。
     *
     * @param dataSource Core 数据源
     */
    public PostgresPurchasingReferencePort(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(new TransactionAwareDataSourceProxy(dataSource));
    }

    /**
     * 按可信租户查询启用供应商。
     */
    @Override
    public boolean isActiveSupplier(UUID tenantId, UUID supplierId) {
        if (tenantId == null || supplierId == null) {
            return false;
        }
        return database(() -> jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM md_supplier
                     WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND isdel = 0
                )
                """, Boolean.class, tenantId, supplierId));
    }

    /**
     * 按可信租户读取启用商品的计量单位和批次标记。
     */
    @Override
    public Optional<PurchasingProductFact> findActiveProduct(UUID tenantId, UUID productId) {
        if (tenantId == null || productId == null) {
            return Optional.empty();
        }
        return database(() -> jdbcTemplate.query("""
                SELECT id, tenant_id, uom, batch_managed
                  FROM md_product
                 WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND isdel = 0
                 LIMIT 1
                """, (resultSet, rowNum) -> new PurchasingProductFact(
                resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("uom"), resultSet.getBoolean("batch_managed")), tenantId, productId)
                .stream().findFirst());
    }

    /**
     * 按可信租户查询启用仓库。
     */
    @Override
    public boolean isActiveWarehouse(UUID tenantId, UUID warehouseId) {
        if (tenantId == null || warehouseId == null) {
            return false;
        }
        return database(() -> jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM md_warehouse
                     WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND isdel = 0
                )
                """, Boolean.class, tenantId, warehouseId));
    }

    /**
     * 按可信租户读取启用库位及所属仓库。
     */
    @Override
    public Optional<PurchasingLocationFact> findActiveLocation(UUID tenantId, UUID locationId) {
        if (tenantId == null || locationId == null) {
            return Optional.empty();
        }
        return database(() -> jdbcTemplate.query("""
                SELECT id, tenant_id, warehouse_id, type, status
                  FROM md_location
                 WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE' AND isdel = 0
                 LIMIT 1
                """, (resultSet, rowNum) -> new PurchasingLocationFact(
                resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("warehouse_id", UUID.class), resultSet.getString("type"),
                resultSet.getString("status")), tenantId, locationId).stream().findFirst());
    }

    private <T> T database(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BaseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("采购主数据查询暂时不可用", exception);
        }
    }
}
