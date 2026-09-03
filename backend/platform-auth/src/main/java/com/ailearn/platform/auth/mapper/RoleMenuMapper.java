package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.RoleMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 角色菜单关联 Mapper。
 */
@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {

    /**
     * 逻辑删除指定角色的所有菜单关联（置 isdel = 1）。
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    @Update("UPDATE auth_role_menu SET isdel = 1 WHERE role_id = #{roleId} AND isdel = 0")
    int deleteByRoleId(@Param("roleId") UUID roleId);
}
