package com.ailearn.platform.core.masterdata.infrastructure.mapper;

import com.ailearn.platform.core.masterdata.domain.entity.Warehouse;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仓库 MyBatis-Plus Mapper。
 */
@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
}
