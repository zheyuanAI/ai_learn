package com.ailearn.platform.core.masterdata.infrastructure.mapper;

import com.ailearn.platform.core.masterdata.domain.entity.Uom;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计量单位 MyBatis-Plus Mapper。
 * <p>
 * 仅提供表级持久化入口，租户过滤由对应 Repository 统一追加，禁止应用层直接调用。
 * </p>
 */
@Mapper
public interface UomMapper extends BaseMapper<Uom> {
}
