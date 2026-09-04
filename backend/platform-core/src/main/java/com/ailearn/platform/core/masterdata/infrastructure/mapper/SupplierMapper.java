package com.ailearn.platform.core.masterdata.infrastructure.mapper;

import com.ailearn.platform.core.masterdata.domain.entity.Supplier;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 供应商 MyBatis-Plus Mapper。
 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {
}
