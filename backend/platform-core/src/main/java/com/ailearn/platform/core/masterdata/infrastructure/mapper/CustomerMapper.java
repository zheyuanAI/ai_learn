package com.ailearn.platform.core.masterdata.infrastructure.mapper;

import com.ailearn.platform.core.masterdata.domain.entity.Customer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户 MyBatis-Plus Mapper。
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
}
