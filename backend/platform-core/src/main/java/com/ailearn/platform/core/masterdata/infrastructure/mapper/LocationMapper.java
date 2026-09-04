package com.ailearn.platform.core.masterdata.infrastructure.mapper;

import com.ailearn.platform.core.masterdata.domain.entity.Location;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库位 MyBatis-Plus Mapper。
 */
@Mapper
public interface LocationMapper extends BaseMapper<Location> {
}
