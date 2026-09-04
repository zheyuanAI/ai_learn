package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.entity.Uom;
import com.ailearn.platform.core.masterdata.infrastructure.mapper.UomMapper;
import org.springframework.stereotype.Repository;

/**
 * 计量单位主数据 Repository。
 */
@Repository
public class UomRepository extends AbstractMyBatisMasterDataRepository<Uom> {

    public UomRepository(UomMapper mapper) {
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
