package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.entity.Menu;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 菜单 Mapper 数据访问接口。
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    /**
     * 根据用户 ID 与租户 ID 联查用户关联的全部有效且可见的菜单列表（XML SQL 实现，按层级和序号排序）。
     * <p>
     * 【用途】获取用户在当前租户下可展示的侧边栏动态菜单。
     * 主要入参：tenantId (租户ID), userId (用户ID)；
     * 返回结果：当前用户可见且启用的菜单实体列表；
     * 简要流程：联查 auth_menu, auth_role_menu, auth_user_role, auth_role，限定 tenant_id、visible=true、status=ACTIVE 且未删除。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 菜单列表
     */
    List<Menu> findMenusByUserIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 查询指定租户下所有启用的菜单。
     *
     * @param tenantId 租户 ID
     * @return 菜单列表
     */
    @Select("SELECT * FROM auth_menu WHERE tenant_id = #{tenantId} AND status = 'ACTIVE' AND visible = true AND isdel = 0 ORDER BY parent_id ASC, sort_order ASC")
    List<Menu> findAllActiveMenus(@Param("tenantId") UUID tenantId);

    /**
     * 查询指定租户内所有未删除的完整菜单列表（XML SQL 实现，按父级层级与同级序号稳定排序）。
     * <p>
     * 【修改方法】用于管理后台构建当前租户的完整菜单树。
     * 主要入参：tenantId (租户ID)；
     * 返回结果：包含各层级节点的租户未删除菜单集合；
     * 简要流程：按 tenant_id 过滤，parent_id 升序（NULL 优先）、sort_order 升序及 created_at 升序稳定排列。
     * </p>
     *
     * @param tenantId 租户 ID
     * @return 菜单全量列表
     */
    List<Menu> findAllMenusForAdmin(@Param("tenantId") UUID tenantId);

    /**
     * 一次性查询当前租户内可用于授权的菜单。
     * <p>
     * 【新增方法】在替换角色菜单关联前，批量校验菜单存在、租户归属、未删除及启用状态。
     * 主要入参：tenantId (租户ID), menuIds (待校验菜单ID集合)；
     * 返回结果：同时满足全部条件的菜单实体；
     * 简要流程：单条 SQL 按租户、ID 集合、isdel=0、status=ACTIVE 过滤，避免逐条查询和部分写入。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param menuIds  待校验菜单 ID 集合
     * @return 当前租户内有效且启用的菜单
     */
    List<Menu> findActiveMenusByIdsAndTenantId(@Param("tenantId") UUID tenantId,
                                               @Param("menuIds") List<UUID> menuIds);

    /**
     * 校验租户内菜单编码是否已被其他未删除菜单占用。
     * <p>
     * 【修改方法】用于创建/修改菜单时的编码唯一性校验（租户隔离）。
     * 主要入参：tenantId (租户ID), menuCode (待校验编码), excludeMenuId (排除的菜单ID)；
     * 返回结果：true 表示已存在冲突，false 表示可用；
     * 简要流程：查询同租户内未删除且排除当前菜单ID的同名编码数量。
     * </p>
     *
     * @param tenantId      租户 ID
     * @param menuCode      菜单编码
     * @param excludeMenuId 排除的菜单 ID（可为 null）
     * @return 若已存在返回 true，否则 false
     */
    default boolean existsByMenuCode(UUID tenantId, String menuCode, UUID excludeMenuId) {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<Menu>()
                .eq(Menu::getTenantId, tenantId)
                .eq(Menu::getMenuCode, menuCode)
                .eq(Menu::getIsdel, 0);
        if (excludeMenuId != null) {
            wrapper.ne(Menu::getId, excludeMenuId);
        }
        return selectCount(wrapper) > 0;
    }

    /**
     * 统计指定菜单在特定租户下的直接未删除子菜单数量（XML SQL 实现）。
     * <p>
     * 【修改方法】用于删除菜单前的 409 业务冲突检查（含有子菜单禁止删除）。
     * 主要入参：tenantId (租户ID), parentId (父菜单ID)；
     * 返回结果：直接子菜单数量；
     * 简要流程：统计 auth_menu 中 tenant_id 等于入参、parent_id 等于入参且 isdel = 0 的记录数。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param parentId 父菜单 ID
     * @return 子菜单数量
     */
    int countChildrenByParentId(@Param("tenantId") UUID tenantId, @Param("parentId") UUID parentId);

    /**
     * 统计指定租户内已授权该菜单的未删除角色关联数量（XML SQL 实现）。
     * <p>
     * 【修改方法】用于删除菜单前的 409 业务冲突检查（已被角色授权禁止删除）。
     * 主要入参：tenantId (租户ID), menuId (菜单ID)；
     * 返回结果：引用该菜单的角色关联数量；
     * 简要流程：联查 auth_role_menu 与 auth_role，统计同租户内 menu_id 等于入参且 isdel = 0 的记录数。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param menuId   菜单 ID
     * @return 关联的角色数量
     */
    int countAssignedRoles(@Param("tenantId") UUID tenantId, @Param("menuId") UUID menuId);

    /**
     * 根据角色 ID 查询该角色分配的有效菜单 ID 列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于角色管理中读取角色已勾选的菜单清单。
     * 主要入参：roleId (角色ID)；
     * 返回结果：菜单 UUID 列表；
     * 简要流程：查询 auth_role_menu 关联的未删除 menu_id 列表。
     * </p>
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    List<UUID> findMenuIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据角色 ID 查询该角色分配的有效菜单实体列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于按角色读取其拥有的菜单子集。
     * 主要入参：roleId (角色ID)；
     * 返回结果：菜单实体列表；
     * 简要流程：联查 auth_menu 与 auth_role_menu，按层级与序号排序。
     * </p>
     *
     * @param roleId 角色 ID
     * @return 菜单列表
     */
    List<Menu> findMenusByRoleId(@Param("roleId") UUID roleId);
}
