package com.ailearn.platform.auth.controller.admin;

import com.ailearn.platform.auth.domain.dto.admin.MenuCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.MenuAdminNodeVo;
import com.ailearn.platform.auth.service.admin.MenuAdminService;
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
 * 菜单后台管理 REST 控制器。
 * <p>
 * 提供系统动态菜单树全量检索、菜单节点增删改、防环路修改、显隐切换与删除前 409 冲突防御端点。
 * </p>
 */
@Tag(name = "菜单后台管理", description = "提供系统动态菜单树全量构建与节点维护端点")
@RestController
@RequestMapping("/api/auth/admin/menus")
public class MenuAdminController {

    private final MenuAdminService menuAdminService;

    public MenuAdminController(MenuAdminService menuAdminService) {
        this.menuAdminService = menuAdminService;
    }

    /**
     * 获取全量系统动态菜单树。
     * <p>
     * 【用途】供管理后台菜单列表以树形表格展示完整的菜单节点层级与顺序。
     * 主要入参：无；
     * 返回结果：ApiResponse&lt;List&lt;MenuAdminNodeVo&gt;&gt; 嵌套菜单树；
     * 简要流程：调用 menuAdminService.getMenuTree()。
     * </p>
     *
     * @return 统一响应包装的动态菜单树根节点列表
     */
    @Operation(summary = "获取系统全量菜单树", description = "返回当前租户未删除菜单构成的完整嵌套树形结构（含菜单 status，按 sortOrder 稳定排序）")
    @GetMapping
    public ApiResponse<List<MenuAdminNodeVo>> getMenuTree() {
        List<MenuAdminNodeVo> tree = menuAdminService.getMenuTree();
        return ApiResponse.ok(tree);
    }

    /**
     * 获取指定菜单节点的详细配置。
     * <p>
     * 【用途】供编辑菜单弹窗回显。
     * 主要入参：menuId (目标菜单ID)；
     * 返回结果：ApiResponse&lt;MenuAdminNodeVo&gt; 菜单节点画像；
     * 简要流程：调用 menuAdminService.getMenuDetail(menuId)。
     * </p>
     *
     * @param menuId 目标菜单 ID
     * @return 统一响应包装的菜单节点详情
     */
    @Operation(summary = "获取菜单节点详情", description = "读取指定菜单节点的路由、组件、图标与排序配置")
    @GetMapping("/{menuId}")
    public ApiResponse<MenuAdminNodeVo> getMenuDetail(
            @Parameter(description = "菜单唯一标识 ID", required = true)
            @PathVariable("menuId") UUID menuId) {
        MenuAdminNodeVo vo = menuAdminService.getMenuDetail(menuId);
        return ApiResponse.ok(vo);
    }

    /**
     * 创建新菜单节点。
     * <p>
     * 【用途】供管理员添加顶级或二级菜单。
     * 主要入参：request (菜单属性请求体)；
     * 返回结果：ApiResponse&lt;MenuAdminNodeVo&gt; 创建成功的菜单详情；
     * 简要流程：参数校验 -> 调用 menuAdminService.createMenu(request)。
     * </p>
     *
     * @param request 菜单创建请求体
     * @return 统一响应包装的创建后菜单视图对象
     */
    @Operation(summary = "创建菜单节点", description = "新增顶级菜单或子级路由节点，自动校验编码唯一性")
    @PostMapping
    public ApiResponse<MenuAdminNodeVo> createMenu(@Valid @RequestBody MenuCreateRequest request) {
        MenuAdminNodeVo vo = menuAdminService.createMenu(request);
        return ApiResponse.ok(vo);
    }

    /**
     * 修改菜单节点属性（含防成环校验）。
     * <p>
     * 【用途】供管理员调整菜单配置或父子层级。
     * 主要入参：menuId (目标菜单ID), request (修改请求体)；
     * 返回结果：ApiResponse&lt;MenuAdminNodeVo&gt; 修改后的菜单详情；
     * 简要流程：参数校验 -> 调用 menuAdminService.updateMenu(menuId, request)。
     * </p>
     *
     * @param menuId  目标菜单 ID
     * @param request 菜单修改请求体
     * @return 统一响应包装的更新后菜单视图对象
     */
    @Operation(summary = "修改菜单节点", description = "更新菜单名称、路由组件、图标及父级节点，严格校验防止形成死循环循环引用")
    @PutMapping("/{menuId}")
    public ApiResponse<MenuAdminNodeVo> updateMenu(
            @Parameter(description = "菜单唯一标识 ID", required = true)
            @PathVariable("menuId") UUID menuId,
            @Valid @RequestBody MenuUpdateRequest request) {
        MenuAdminNodeVo vo = menuAdminService.updateMenu(menuId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 快速更新菜单启用状态。
     * <p>
     * 【用途】供管理员在表格快速启用或停用菜单，显隐属性由普通更新接口维护。
     * 主要入参：menuId (目标菜单ID), request (status 状态)；
     * 返回结果：ApiResponse&lt;MenuAdminNodeVo&gt; 更新后的菜单详情；
     * 简要流程：参数校验 -> 调用 menuAdminService.updateMenuStatus(menuId, request)。
     * </p>
     *
     * @param menuId  目标菜单 ID
     * @param request 启停状态更新请求体
     * @return 统一响应包装的更新后菜单视图对象
     */
    @Operation(summary = "变更菜单启用状态", description = "快速切换指定菜单的 ACTIVE/DISABLED 状态；visible 显隐属性由菜单更新接口维护")
    @PutMapping("/{menuId}/status")
    public ApiResponse<MenuAdminNodeVo> updateMenuStatus(
            @Parameter(description = "菜单唯一标识 ID", required = true)
            @PathVariable("menuId") UUID menuId,
            @Valid @RequestBody MenuStatusUpdateRequest request) {
        MenuAdminNodeVo vo = menuAdminService.updateMenuStatus(menuId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 删除指定菜单节点（软删除）。
     * <p>
     * 【用途】供管理员删除废弃菜单。
     * 主要入参：menuId (目标菜单ID)；
     * 返回结果：ApiResponse&lt;Void&gt; 成功响应；
     * 简要流程：调用 menuAdminService.deleteMenu(menuId)（若有子菜单或已被角色引用则返回 HTTP 409 业务冲突）。
     * </p>
     *
     * @param menuId 目标菜单 ID
     * @return 统一成功响应
     */
    @Operation(summary = "删除菜单节点", description = "软删除菜单节点，若存在直接子菜单或已被角色引用则返回 HTTP 409 冲突拒绝删除")
    @DeleteMapping("/{menuId}")
    public ApiResponse<Void> deleteMenu(
            @Parameter(description = "菜单唯一标识 ID", required = true)
            @PathVariable("menuId") UUID menuId) {
        menuAdminService.deleteMenu(menuId);
        return ApiResponse.ok();
    }
}
