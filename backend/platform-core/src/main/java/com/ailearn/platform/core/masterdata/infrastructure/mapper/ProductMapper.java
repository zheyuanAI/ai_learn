package com.ailearn.platform.core.masterdata.infrastructure.mapper;

import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 MyBatis-Plus Mapper。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
