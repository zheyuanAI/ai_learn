package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.infrastructure.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Repository;

/**
 * 商品主数据 Repository。
 */
@Repository
public class ProductRepository extends AbstractMyBatisMasterDataRepository<Product> {

    public ProductRepository(ProductMapper mapper) {
        super(mapper);
    }

    @Override
    protected String codeColumn() {
        return "sku";
    }

    @Override
    protected String nameColumn() {
        return "name";
    }

    @Override
    protected void applySpecificFilters(QueryWrapper<Product> wrapper, MasterDataPageQuery query) {
        if (query.getCategory() != null) {
            wrapper.eq("category", query.getCategory());
        }
        if (query.getBatchManaged() != null) {
            wrapper.eq("batch_managed", query.getBatchManaged());
        }
    }
}
