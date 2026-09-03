package com.ailearn.platform.auth.service.admin;

import com.ailearn.platform.auth.domain.dto.admin.RoleCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleMenusAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RolePermissionsAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleQueryRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.RoleAdminVo;
import java.util.List;
import java.util.UUID;

/**
 * 角色后台管理业务服务接口。
 * <p>
 * 提供角色列表查询、详情读取、新增角色、修改角色、启停用、权限全量替换分配、菜单全量替换分配、删除与防预置角色误删保护。
 * </p>
 */
public interface RoleAdminService {

    /**
     * 根据条件查询当前租户内的角色列表（含统计指标）。
     * <p>
     * 【用途】供管理后台角色管理表格展示与条件检索。
     * 主要入参：request (角色编码/名称模糊、状态精确)；
     * 返回结果：包含关联用户数与权限点数统计的 RoleAdminVo 列表；
     * 简要流程：从上下文获取租户 ID，执行条件查询，装配关联统计后返回。
     * </p>
     *
     * @param request 角色查询筛选参数
     * @return 角色管理视图对象列表
     */
    List<RoleAdminVo> listRoles(RoleQueryRequest request);

    /**
     * 查询指定角色的详细配置（含关联权限与菜单 ID 集合）。
     * <p>
     * 【用途】供管理后台查看或编辑角色的详细属性与授权勾选树。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：RoleAdminVo 角色画像与关联 ID 列表；
     * 简要流程：核验租户隔离，查询角色实体及关联的 permission_id 和 menu_id 集合。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 角色管理视图对象
     */
    RoleAdminVo getRoleDetail(UUID roleId);

    /**
     * 创建新业务角色。
     * <p>
     * 【用途】供管理员在当前租户内定义新角色并绑定初始权限与菜单。
     * 主要入参：request (roleCode, roleName, description, permissionIds, menuIds)；
     * 返回结果：创建成功后的 RoleAdminVo 角色详情；
     * 简要流程：核验租户内编码唯一性，保存角色实体并插入权限与菜单关系。
     * </p>
     *
     * @param request 角色创建请求参数
     * @return 创建后的角色管理视图对象
     */
    RoleAdminVo createRole(RoleCreateRequest request);

    /**
     * 修改角色展示信息与授权。
     * <p>
     * 【用途】供管理员修改角色名称、描述或重新全量指派权限与菜单。
     * 主要入参：roleId (目标角色ID), request (修改参数)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：核验租户隔离与基础角色保护，更新角色字段，重置权限与菜单关联，清理受影响用户的 Redis 权限缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 角色修改请求参数
     * @return 更新后的角色管理视图对象
     */
    RoleAdminVo updateRole(UUID roleId, RoleUpdateRequest request);

    /**
     * 变更角色的启用状态（正常/禁用）。
     * <p>
     * 【用途】供管理员启停用特定业务角色。
     * 主要入参：roleId (目标角色ID), request (目标状态 ACTIVE/DISABLED)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：禁止停用预置系统管理员角色，更新状态并清理受影响用户的权限缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 状态变更请求参数
     * @return 更新后的角色管理视图对象
     */
    RoleAdminVo updateRoleStatus(UUID roleId, RoleStatusUpdateRequest request);

    /**
     * 为指定角色全量分配功能权限点。
     * <p>
     * 【用途】供管理员在角色授权界面批量保存权限勾选结果。
     * 主要入参：roleId (目标角色ID), request (权限点ID列表)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：核验租户隔离，事务化全量替换 role_permission 关联记录，批量清除所有属于该角色的用户权限缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 权限分配请求参数
     * @return 更新后的角色管理视图对象
     */
    RoleAdminVo assignPermissions(UUID roleId, RolePermissionsAssignRequest request);

    /**
     * 为指定角色全量分配动态菜单。
     * <p>
     * 【用途】供管理员在角色授权界面批量保存菜单勾选树。
     * 主要入参：roleId (目标角色ID), request (菜单ID列表)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：核验租户隔离，事务化全量替换 role_menu 关联记录，批量清除所有属于该角色的用户菜单缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 菜单分配请求参数
     * @return 更新后的角色管理视图对象
     */
    RoleAdminVo assignMenus(UUID roleId, RoleMenusAssignRequest request);

    /**
     * 删除指定业务角色（软删除）。
     * <p>
     * 【用途】供管理员清理废弃角色。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：无；
     * 简要流程：禁止删除系统预置管理员角色，检查用户关联冲突（若已被分配用户则抛出 409 冲突异常），执行软删除并解除所有权限与菜单关系。
     * </p>
     *
     * @param roleId 目标角色 ID
     */
    void deleteRole(UUID roleId);
}
