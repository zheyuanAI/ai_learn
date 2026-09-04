package com.ailearn.platform.core.masterdata.infrastructure.repository;

import com.ailearn.platform.core.masterdata.domain.entity.Customer;
import com.ailearn.platform.core.masterdata.infrastructure.mapper.CustomerMapper;
import org.springframework.stereotype.Repository;

/**
 * 客户主数据 Repository。
 */
@Repository
public class CustomerRepository extends AbstractMyBatisMasterDataRepository<Customer> {

    public CustomerRepository(CustomerMapper mapper) {
        super(mapper);
    }

    @Override
    protected String codeColumn() {
        return "customer_code";
    }

    @Override
    protected String nameColumn() {
        return "customer_name";
    }
}
