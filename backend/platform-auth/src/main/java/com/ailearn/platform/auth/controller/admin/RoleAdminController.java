package com.ailearn.platform.auth.controller.admin;

import com.ailearn.platform.auth.domain.dto.admin.RoleCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleMenusAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RolePermissionsAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleQueryRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.PermissionAdminVo;
import com.ailearn.platform.auth.domain.vo.admin.RoleAdminVo;
import com.ailearn.platform.auth.service.admin.MenuAdminService;
import com.ailearn.platform.auth.service.admin.PermissionAdminService;
import com.ailearn.platform.auth.service.admin.RoleAdminService;
import com.ailearn.platform.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色后台管理 REST 控制器。
 * <p>
 * 提供角色列表检索、角色配置维护、权限与菜单勾选授权、启停用与删除冲突防御端点。
 * </p>
 */
@Tag(name = "角色后台管理", description = "提供角色 CRUD、权限点配置与菜单授权端点")
@RestController
@RequestMapping("/api/auth/admin/roles")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;
    private final PermissionAdminService permissionAdminService;
    private final MenuAdminService menuAdminService;

    public RoleAdminController(RoleAdminService roleAdminService,
                               PermissionAdminService permissionAdminService,
                               MenuAdminService menuAdminService) {
        this.roleAdminService = roleAdminService;
        this.permissionAdminService = permissionAdminService;
        this.menuAdminService = menuAdminService;
    }

    /**
     * 查询当前租户内的角色列表（含用户数与权限数统计）。
     * <p>
     * 【用途】供管理后台角色管理列表展示与条件过滤。
     * 主要入参：request (角色编码/名称模糊、状态精确)；
     * 返回结果：ApiResponse&lt;List&lt;RoleAdminVo&gt;&gt; 角色列表；
     * 简要流程：调用 roleAdminService.listRoles(request)。
     * </p>
     *
     * @param request 角色查询筛选参数
     * @return 统一响应包装的角色列表
     */
    @Operation(summary = "查询角色列表", description = "支持按角色业务编码、名称与启用状态检索，返回包含关联用户数和权限点数统计")
    @GetMapping
    public ApiResponse<List<RoleAdminVo>> listRoles(@Valid RoleQueryRequest request) {
        List<RoleAdminVo> list = roleAdminService.listRoles(request);
        return ApiResponse.ok(list);
    }

    /**
     * 获取指定角色的详细配置画像。
     * <p>
     * 【用途】供编辑角色弹窗回显属性。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：ApiResponse&lt;RoleAdminVo&gt; 角色画像；
     * 简要流程：调用 roleAdminService.getRoleDetail(roleId)。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 统一响应包装的角色视图对象
     */
    @Operation(summary = "获取角色详情", description = "读取指定角色的属性详情及已绑定的权限点与菜单 ID 清单")
    @GetMapping("/{roleId}")
    public ApiResponse<RoleAdminVo> getRoleDetail(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId) {
        RoleAdminVo vo = roleAdminService.getRoleDetail(roleId);
        return ApiResponse.ok(vo);
    }

    /**
     * 创建新业务角色。
     * <p>
     * 【用途】供管理员定义新角色并绑定初始权限。
     * 主要入参：request (包含 roleCode, roleName, description, permissionIds, menuIds)；
     * 返回结果：ApiResponse&lt;RoleAdminVo&gt; 创建成功的角色详情；
     * 简要流程：参数校验 -> 调用 roleAdminService.createRole(request)。
     * </p>
     *
     * @param request 角色创建请求体
     * @return 统一响应包装的创建后角色视图对象
     */
    @Operation(summary = "创建角色", description = "在当前租户下创建新角色，并可选分配初始权限点与菜单")
    @PostMapping
    public ApiResponse<RoleAdminVo> createRole(@Valid @RequestBody RoleCreateRequest request) {
        RoleAdminVo vo = roleAdminService.createRole(request);
        return ApiResponse.ok(vo);
    }

    /**
     * 修改角色展示属性与授权。
     * <p>
     * 【用途】供管理员修改角色名称、描述或重设权限菜单。
     * 主要入参：roleId (目标角色ID), request (修改请求体)；
     * 返回结果：ApiResponse&lt;RoleAdminVo&gt; 修改后的角色详情；
     * 简要流程：参数校验 -> 调用 roleAdminService.updateRole(roleId, request)。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 角色修改请求体
     * @return 统一响应包装的更新后角色视图对象
     */
    @Operation(summary = "修改角色信息", description = "更新角色的展示名称与描述，支持全量替换权限点与菜单集合")
    @PutMapping("/{roleId}")
    public ApiResponse<RoleAdminVo> updateRole(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody RoleUpdateRequest request) {
        RoleAdminVo vo = roleAdminService.updateRole(roleId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 变更角色的启用状态（正常/禁用）。
     * <p>
     * 【用途】供管理员启停用角色。
     * 主要入参：roleId (目标角色ID), request (目标状态)；
     * 返回结果：ApiResponse&lt;RoleAdminVo&gt; 更新后的角色详情；
     * 简要流程：参数校验 -> 调用 roleAdminService.updateRoleStatus(roleId, request)。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 状态修改请求体
     * @return 统一响应包装的角色视图对象
     */
    @Operation(summary = "变更角色状态", description = "启停用指定角色，禁止停用系统预置管理员角色")
    @PutMapping("/{roleId}/status")
    public ApiResponse<RoleAdminVo> updateRoleStatus(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody RoleStatusUpdateRequest request) {
        RoleAdminVo vo = roleAdminService.updateRoleStatus(roleId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 查询指定角色已分配的权限点列表。
     * <p>
     * 【用途】供角色权限授权回显。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：ApiResponse&lt;List&lt;PermissionAdminVo&gt;&gt; 权限点列表；
     * 简要流程：调用 permissionAdminService.getRolePermissions(roleId)。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 统一响应包装的权限点列表
     */
    @Operation(summary = "查询角色拥有的权限点列表", description = "读取指定角色已授权的全部功能权限点详情")
    @GetMapping("/{roleId}/permissions")
    public ApiResponse<List<PermissionAdminVo>> getRolePermissions(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId) {
        List<PermissionAdminVo> list = permissionAdminService.getRolePermissions(roleId);
        return ApiResponse.ok(list);
    }

    /**
     * 为角色重新全量分配功能权限点。
     * <p>
     * 【用途】供管理员保存角色的权限勾选。
     * 主要入参：roleId (目标角色ID), request (权限点ID列表)；
     * 返回结果：ApiResponse&lt;RoleAdminVo&gt; 更新后的角色详情；
     * 简要流程：参数校验 -> 调用 roleAdminService.assignPermissions(roleId, request)。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 权限分配请求体
     * @return 统一响应包装的角色视图对象
     */
    @Operation(summary = "分配角色功能权限点", description = "全量替换指定角色的权限点关联，并清理关联用户的权限缓存")
    @PutMapping("/{roleId}/permissions")
    public ApiResponse<RoleAdminVo> assignPermissions(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody RolePermissionsAssignRequest request) {
        RoleAdminVo vo = roleAdminService.assignPermissions(roleId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 查询指定角色已分配的菜单 ID 列表。
     * <p>
     * 【用途】供角色菜单树授权回显已选 ID。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：ApiResponse&lt;List&lt;UUID&gt;&gt; 菜单 ID 列表；
     * 简要流程：调用 menuAdminService.getRoleMenuIds(roleId)。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 统一响应包装的菜单 ID 列表
     */
    @Operation(summary = "查询角色拥有的菜单 ID 列表", description = "读取指定角色已授权勾选的菜单节点 ID 清单")
    @GetMapping("/{roleId}/menus")
    public ApiResponse<List<UUID>> getRoleMenus(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId) {
        List<UUID> list = menuAdminService.getRoleMenuIds(roleId);
        return ApiResponse.ok(list);
    }

    /**
     * 为角色重新全量分配动态菜单。
     * <p>
     * 【用途】供管理员保存角色的菜单勾选树。
     * 主要入参：roleId (目标角色ID), request (菜单ID列表)；
     * 返回结果：ApiResponse&lt;RoleAdminVo&gt; 更新后的角色详情；
     * 简要流程：参数校验 -> 调用 roleAdminService.assignMenus(roleId, request)。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 菜单分配请求体
     * @return 统一响应包装的角色视图对象
     */
    @Operation(summary = "分配角色菜单", description = "全量替换指定角色的菜单关联，并清理关联用户的菜单缓存")
    @PutMapping("/{roleId}/menus")
    public ApiResponse<RoleAdminVo> assignMenus(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId,
            @Valid @RequestBody RoleMenusAssignRequest request) {
        RoleAdminVo vo = roleAdminService.assignMenus(roleId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 删除指定业务角色（软删除）。
     * <p>
     * 【用途】供管理员删除废弃角色。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：ApiResponse&lt;Void&gt; 成功响应；
     * 简要流程：调用 roleAdminService.deleteRole(roleId)（若已分配用户则返回 HTTP 409 业务冲突）。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 统一成功响应
     */
    @Operation(summary = "删除角色", description = "软删除角色，若角色已分配给任何用户将返回 HTTP 409 冲突拒绝删除")
    @DeleteMapping("/{roleId}")
    public ApiResponse<Void> deleteRole(
            @Parameter(description = "角色唯一标识 ID", required = true)
            @PathVariable("roleId") UUID roleId) {
        roleAdminService.deleteRole(roleId);
        return ApiResponse.ok();
    }
}
