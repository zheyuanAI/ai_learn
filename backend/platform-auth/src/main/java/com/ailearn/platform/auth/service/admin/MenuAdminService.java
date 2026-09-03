package com.ailearn.platform.auth.service.admin;

import com.ailearn.platform.auth.domain.dto.admin.MenuCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.MenuAdminNodeVo;
import java.util.List;
import java.util.UUID;

/**
 * 菜单后台管理业务服务接口。
 * <p>
     * 提供系统动态菜单树全量构建、菜单节点维护、防环路修改、显隐/启停切换、删除前 409 冲突检查及角色菜单查询能力。
 * </p>
 */
public interface MenuAdminService {

    /**
     * 获取全量系统动态菜单树结构。
     * <p>
     * 【用途】供管理后台菜单配置列表以树形表格展示全部菜单节点与层级。
     * 主要入参：无；
     * 返回结果：嵌套结构的 MenuAdminNodeVo 列表（按同级排序权重升序）；
     * 简要流程：查询全局未删除菜单列表，在内存中递归构建多叉树结构并排序。
     * </p>
     *
     * @return 动态菜单树根节点列表
     */
    List<MenuAdminNodeVo> getMenuTree();

    /**
     * 查询指定菜单节点的详细信息。
     * <p>
     * 【用途】供编辑菜单弹窗回显配置。
     * 主要入参：menuId (菜单ID)；
     * 返回结果：MenuAdminNodeVo 菜单详情；
     * 简要流程：按 ID 检索未删除菜单并转换为 VO。
     * </p>
     *
     * @param menuId 目标菜单 ID
     * @return 菜单节点详情视图对象
     */
    MenuAdminNodeVo getMenuDetail(UUID menuId);

    /**
     * 创建新菜单节点。
     * <p>
     * 【用途】供管理员添加顶级菜单模块或子路由节点。
     * 主要入参：request (menuCode, menuName, routePath, componentPath, icon, sortOrder, permissionCode, visible, parentId)；
     * 返回结果：创建成功后的 MenuAdminNodeVo；
     * 简要流程：核验菜单编码唯一性与父节点合法性，插入记录并返回详情。
     * </p>
     *
     * @param request 创建菜单请求参数
     * @return 创建后的菜单视图对象
     */
    MenuAdminNodeVo createMenu(MenuCreateRequest request);

    /**
     * 修改指定菜单节点的属性（含防环路校验）。
     * <p>
     * 【用途】供管理员调整菜单编码、名称、路由组件、图标、同级排序或调整父级层级。
     * 主要入参：menuId (目标菜单ID), request (修改字段集合)；
     * 返回结果：更新后的 MenuAdminNodeVo；
     * 简要流程：检查菜单存在性，校验父节点非自身且非自身后代节点（防死循环树），更新实体并返回。
     * </p>
     *
     * @param menuId  目标菜单 ID
     * @param request 修改菜单请求参数
     * @return 更新后的菜单视图对象
     */
    MenuAdminNodeVo updateMenu(UUID menuId, MenuUpdateRequest request);

    /**
     * 快速更新菜单启用状态。
     * <p>
     * 【用途】供管理员在表格快速启用或停用菜单，visible 由普通更新接口维护。
     * 主要入参：menuId (目标菜单ID), request (status 状态)；
     * 返回结果：更新后的 MenuAdminNodeVo；
     * 简要流程：更新菜单 status 状态字段并落库。
     * </p>
     *
     * @param menuId  目标菜单 ID
     * @param request 启停更新请求参数
     * @return 更新后的菜单视图对象
     */
    MenuAdminNodeVo updateMenuStatus(UUID menuId, MenuStatusUpdateRequest request);

    /**
     * 删除指定菜单节点（软删除）。
     * <p>
     * 【用途】供管理员删除无用的废弃菜单。
     * 主要入参：menuId (目标菜单ID)；
     * 返回结果：无；
     * 简要流程：检查子菜单依赖冲突（有子菜单则 409），检查角色授权依赖冲突（被角色引用则 409），软删除菜单。
     * </p>
     *
     * @param menuId 目标菜单 ID
     */
    void deleteMenu(UUID menuId);

    /**
     * 查询指定角色已授权的菜单 ID 列表。
     * <p>
     * 【用途】供角色授权界面菜单树组件回显已勾选的节点 ID 集合。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：菜单 UUID 列表；
     * 简要流程：核验租户隔离，查询 auth_role_menu 中绑定的未删除菜单标识集合。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 菜单 ID 列表
     */
    List<UUID> getRoleMenuIds(UUID roleId);
}
