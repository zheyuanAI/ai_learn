package com.ailearn.platform.auth.service.admin;

import com.ailearn.platform.auth.domain.dto.admin.PermissionQueryRequest;
import com.ailearn.platform.auth.domain.vo.admin.PermissionAdminVo;
import java.util.List;
import java.util.UUID;

/**
 * 权限点后台管理业务服务接口。
 * <p>
 * 提供系统功能权限点字典目录的只读检索与按角色查询权限清单能力。
 * </p>
 */
public interface PermissionAdminService {

    /**
     * 根据模块、编码与名称条件检索系统权限点字典列表。
     * <p>
     * 【用途】供管理后台权限清单页面展示与模块分组筛选。
     * 主要入参：request (module, permissionCode, permissionName)；
     * 返回结果：PermissionAdminVo 列表；
     * 简要流程：按条件过滤并按所属模块和编码升序组织返回。
     * </p>
     *
     * @param request 权限点查询筛选参数
     * @return 权限点视图对象列表
     */
    List<PermissionAdminVo> listPermissions(PermissionQueryRequest request);

    /**
     * 查询指定角色拥有的功能权限点实体列表。
     * <p>
     * 【用途】供管理后台查看指定角色的权限明细画像。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：分配给该角色的 PermissionAdminVo 列表；
     * 简要流程：核验租户隔离，多表联查已授权的未删除权限点清单。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 权限点视图对象列表
     */
    List<PermissionAdminVo> getRolePermissions(UUID roleId);

    /**
     * 查询指定角色拥有的功能权限点 ID 列表。
     * <p>
     * 【用途】供角色编辑授权面板快速回显已勾选的权限点 ID 集合。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：权限点 UUID 列表；
     * 简要流程：核验租户隔离，查询 auth_role_permission 中的 permission_id 集合。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 权限点 ID 列表
     */
    List<UUID> getRolePermissionIds(UUID roleId);
}
