package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.entity.Warehouse;
import com.ailearn.platform.core.masterdata.infrastructure.mapper.WarehouseMapper;
import org.springframework.stereotype.Repository;

/**
 * 仓库主数据 Repository。
 */
@Repository
public class WarehouseRepository extends AbstractMyBatisMasterDataRepository<Warehouse> {

    public WarehouseRepository(WarehouseMapper mapper) {
        super(mapper);
    }

    @Override
    protected String codeColumn() {
        return "code";
    }

    @Override
    protected String nameColumn() {
        return "name";
    }
}
