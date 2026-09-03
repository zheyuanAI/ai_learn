package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.Role;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色 Mapper 数据访问接口。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 根据租户 ID 与角色编码查询有效角色。
     *
     * @param tenantId 租户 ID
     * @param roleCode 角色编码
     * @return 角色实体或 null
    */
    default Role findByTenantIdAndRoleCode(UUID tenantId, String roleCode) {
        // 修改：排除逻辑删除角色，避免按编码读取到已失效的历史记录。
        return selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getRoleCode, roleCode)
                .eq(Role::getIsdel, 0));
    }

    /**
     * 查询指定租户下的全部有效角色。
     *
     * @param tenantId 租户 ID
     * @return 角色列表
     */
    default List<Role> findActiveRolesByTenantId(UUID tenantId) {
        return selectList(new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getStatus, "ACTIVE")
                .eq(Role::getIsdel, 0)
                .orderByAsc(Role::getCreatedAt));
    }

    /**
     * 一次性查询当前租户内可分配的角色。
     * <p>
     * 【新增方法】在替换用户角色关联前，批量校验角色存在、租户归属、未删除及启用状态。
     * 主要入参：tenantId (租户ID), roleIds (待校验角色ID集合)；
     * 返回结果：同时满足全部条件的角色实体；
     * 简要流程：单条 SQL 按租户、ID 集合、isdel=0、status=ACTIVE 过滤，避免逐条查询和部分写入。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param roleIds  待校验角色 ID 集合
     * @return 当前租户内有效且启用的角色
     */
    List<Role> findActiveRolesByIdsAndTenantId(@Param("tenantId") UUID tenantId,
                                               @Param("roleIds") List<UUID> roleIds);

    /**
     * 校验角色编码在租户内是否已被其他未删除角色占用。
     * <p>
     * 【新增方法】用于创建角色时的编码唯一性校验。
     * 主要入参：tenantId (租户ID), roleCode (待校验角色编码), excludeRoleId (排除的角色ID)；
     * 返回结果：true 表示已占用冲突，false 表示可用；
     * 简要流程：查询同租户、未删除且排除当前角色ID的重名记录数。
     * </p>
     *
     * @param tenantId      租户 ID
     * @param roleCode      角色业务编码
     * @param excludeRoleId 排除的角色 ID（可为 null）
     * @return 若已存在返回 true，否则 false
     */
    default boolean existsByRoleCode(UUID tenantId, String roleCode, UUID excludeRoleId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getRoleCode, roleCode)
                .eq(Role::getIsdel, 0);
        if (excludeRoleId != null) {
            wrapper.ne(Role::getId, excludeRoleId);
        }
        return selectCount(wrapper) > 0;
    }

    /**
     * 根据租户 ID 及条件动态查询角色列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于角色管理列表展示与条件模糊检索。
     * 主要入参：tenantId (租户ID), roleCode (编码模糊), roleName (名称模糊), status (状态筛选)；
     * 返回结果：符合条件的角色列表；
     * 简要流程：按条件动态拼接并按创建时间升序排列。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param roleCode 角色编码模糊搜索
     * @param roleName 角色名称模糊搜索
     * @param status   状态精确筛选
     * @return 角色列表
     */
    List<Role> selectRolesByCondition(@Param("tenantId") UUID tenantId,
                                      @Param("roleCode") String roleCode,
                                      @Param("roleName") String roleName,
                                      @Param("status") String status);

    /**
     * 统计已分配该角色的未删除用户数量（XML SQL 实现）。
     * <p>
     * 【新增方法】用于删除角色前的 409 冲突前置检查。
     * 主要入参：tenantId (租户ID), roleId (角色ID)；
     * 返回结果：关联该角色的用户记录数；
     * 简要流程：查询 auth_user_role 中对应 role_id 的有效记录数。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param roleId   角色 ID
     * @return 关联用户数量
     */
    int countAssignedUsers(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);

    /**
     * 统计该角色已分配的功能权限点数量（XML SQL 实现）。
     * <p>
     * 【新增方法】用于角色管理列表展示统计指标。
     * 主要入参：roleId (角色ID)；
     * 返回结果：关联的有效权限点总数；
     * 简要流程：查询 auth_role_permission 中对应 role_id 的有效记录数。
     * </p>
     *
     * @param roleId 角色 ID
     * @return 关联权限点数量
     */
    int countRolePermissions(@Param("roleId") UUID roleId);
}
