package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.Permission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 权限点 Mapper 数据访问接口。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 根据权限编码查询权限点。
     *
     * @param permissionCode 权限编码
     * @return 权限实体或 null
     */
    default Permission findByPermissionCode(String permissionCode) {
        return selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermissionCode, permissionCode));
    }

    /**
     * 根据用户 ID 与租户 ID 联查用户关联的全部功能权限点编码集合（XML SQL 多表联查）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 功能权限点编码集合
     */
    Set<String> findPermissionCodesByUserIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 根据用户 ID 与租户 ID 联查用户关联的全部权限实体列表（XML SQL 多表联查）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 权限实体列表
     */
    List<Permission> findPermissionsByUserIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 多条件动态查询系统权限点字典列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于权限管理列表展示与模块/编码/名称过滤。
     * 主要入参：module (所属模块), permissionCode (编码模糊), permissionName (名称模糊)；
     * 返回结果：符合条件的权限点列表；
     * 简要流程：按 module, permission_code, permission_name 动态筛选并按 module, permission_code 排序。
     * </p>
     *
     * @param module         所属模块
     * @param permissionCode 权限编码模糊
     * @param permissionName 权限名称模糊
     * @return 权限点列表
     */
    List<Permission> selectPermissionsByCondition(@Param("module") String module,
                                                 @Param("permissionCode") String permissionCode,
                                                 @Param("permissionName") String permissionName);

    /**
     * 根据角色 ID 查询已分配的权限点 ID 列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于角色管理中读取角色已授权的权限点清单。
     * 主要入参：roleId (角色ID)；
     * 返回结果：权限点 UUID 列表；
     * 简要流程：查询 auth_role_permission 关联的未删除 permission_id 列表。
     * </p>
     *
     * @param roleId 角色 ID
     * @return 权限点 ID 列表
     */
    List<UUID> findPermissionIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据角色 ID 查询已分配的权限点实体列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于按角色读取其拥有的权限详细画像。
     * 主要入参：roleId (角色ID)；
     * 返回结果：权限实体列表；
     * 简要流程：联查 auth_permission 与 auth_role_permission。
     * </p>
     *
     * @param roleId 角色 ID
     * @return 权限点实体列表
     */
    List<Permission> findPermissionsByRoleId(@Param("roleId") UUID roleId);
}
