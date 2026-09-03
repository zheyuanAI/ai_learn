package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.UserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户角色关联 Mapper。
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /**
     * 逻辑删除指定用户在指定租户下的所有角色关联（置 isdel = 1）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 影响行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE auth_user_role SET isdel = 1 WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND isdel = 0")
    int deleteByUserIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 逻辑删除指定角色在指定租户下的所有用户关联（置 isdel = 1）。
     * <p>
     * 【修改方法】用于删除角色时逻辑清理其绑定的用户关系。
     * 主要入参：tenantId (租户ID), roleId (角色ID)；
     * 返回结果：影响的行数；
     * 简要流程：更新 auth_user_role 中对应有效记录的 isdel 为 1。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param roleId   角色 ID
     * @return 影响行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE auth_user_role SET isdel = 1 WHERE tenant_id = #{tenantId} AND role_id = #{roleId} AND isdel = 0")
    int deleteByRoleIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

    /**
     * 查询分配了指定角色的所有用户 ID 列表。
     * <p>
     * 【新增方法】用于角色权限/菜单变更后批量清理受影响用户的会话与权限缓存。
     * 主要入参：tenantId (租户ID), roleId (角色ID)；
     * 返回结果：用户 UUID 列表；
     * 简要流程：查询 auth_user_role 中关联该角色的用户标识集合。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param roleId   角色 ID
     * @return 用户 ID 列表
     */
    @Select("SELECT user_id FROM auth_user_role WHERE tenant_id = #{tenantId} AND role_id = #{roleId} AND isdel = 0")
    List<UUID> findUserIdsByRoleIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);
}
