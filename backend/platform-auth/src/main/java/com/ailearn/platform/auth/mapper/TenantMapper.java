package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.Tenant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 Mapper 数据访问接口。
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    /**
     * 根据租户业务编码查询有效租户。
     *
     * @param tenantCode 租户业务编码
     * @return 租户实体，若无返回 null
     */
    default Tenant findByTenantCode(String tenantCode) {
        return selectOne(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantCode, tenantCode));
    }
}
