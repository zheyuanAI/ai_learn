package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.entity.Supplier;
import com.ailearn.platform.core.masterdata.infrastructure.mapper.SupplierMapper;
import org.springframework.stereotype.Repository;

/**
 * 供应商主数据 Repository。
 */
@Repository
public class SupplierRepository extends AbstractMyBatisMasterDataRepository<Supplier> {

    public SupplierRepository(SupplierMapper mapper) {
        super(mapper);
    }

    @Override
    protected String codeColumn() {
        return "supplier_code";
    }

    @Override
    protected String nameColumn() {
        return "supplier_name";
    }
}
