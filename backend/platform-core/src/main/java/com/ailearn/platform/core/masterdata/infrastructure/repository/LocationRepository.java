package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.entity.Location;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.infrastructure.mapper.LocationMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Repository;

/**
 * 库位主数据 Repository。
 */
@Repository
public class LocationRepository extends AbstractMyBatisMasterDataRepository<Location> {

    public LocationRepository(LocationMapper mapper) {
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

    @Override
    protected void applySpecificFilters(QueryWrapper<Location> wrapper, MasterDataPageQuery query) {
        if (query.getWarehouseId() != null) {
            wrapper.eq("warehouse_id", query.getWarehouseId());
        }
        if (query.getType() != null) {
            wrapper.eq("type", query.getType());
        }
    }
}
