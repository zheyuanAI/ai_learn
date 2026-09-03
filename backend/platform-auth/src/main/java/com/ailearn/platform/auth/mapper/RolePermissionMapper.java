package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.RolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 角色权限关联 Mapper。
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 逻辑删除指定角色的所有权限关联（置 isdel = 1）。
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    @Update("UPDATE auth_role_permission SET isdel = 1 WHERE role_id = #{roleId} AND isdel = 0")
    int deleteByRoleId(@Param("roleId") UUID roleId);
}
