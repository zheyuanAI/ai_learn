package com.ailearn.platform.auth.service.admin.impl;

import com.ailearn.platform.auth.domain.dto.admin.MenuCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Menu;
import com.ailearn.platform.auth.domain.entity.Role;
import com.ailearn.platform.auth.domain.vo.admin.MenuAdminNodeVo;
import com.ailearn.platform.auth.mapper.MenuMapper;
import com.ailearn.platform.auth.mapper.RoleMapper;
import com.ailearn.platform.auth.service.admin.MenuAdminService;
import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.exception.BizException;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单后台管理业务服务实现类。
 */
@Service
public class MenuAdminServiceImpl implements MenuAdminService {

    private static final Logger log = LoggerFactory.getLogger(MenuAdminServiceImpl.class);

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;

    public MenuAdminServiceImpl(MenuMapper menuMapper, RoleMapper roleMapper) {
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 获取当前租户的全量动态菜单树结构。
     * <p>
     * 【用途】供管理后台菜单配置列表以树形表格展示全部菜单节点与层级（租户物理隔离）。
     * 主要入参：无（从 TenantContextHolder 读取当前租户 ID）；
     * 返回结果：嵌套结构的 MenuAdminNodeVo 列表；
     * 简要流程：查询当前租户未删除菜单列表，在内存中构建多叉树结构并按序号排序。
     * </p>
     *
     * @return 动态菜单树根节点列表
     */
    @Override
    public List<MenuAdminNodeVo> getMenuTree() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        List<Menu> allMenus = menuMapper.findAllMenusForAdmin(tenantId);
        if (allMenus == null || allMenus.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, MenuAdminNodeVo> nodeMap = new HashMap<>();
        for (Menu menu : allMenus) {
            nodeMap.put(menu.getId(), convertToVo(menu));
        }

        List<MenuAdminNodeVo> roots = new ArrayList<>();
        for (Menu menu : allMenus) {
            MenuAdminNodeVo node = nodeMap.get(menu.getId());
            if (menu.getParentId() == null || !nodeMap.containsKey(menu.getParentId())) {
                roots.add(node);
            } else {
                MenuAdminNodeVo parentNode = nodeMap.get(menu.getParentId());
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList<>());
                }
                parentNode.getChildren().add(node);
            }
        }

        // 递归排序子节点
        sortMenuNodes(roots);
        return roots;
    }

    /**
     * 查询指定菜单节点的详细信息。
     * <p>
     * 【用途】供编辑菜单弹窗回显配置。
     * 主要入参：menuId (菜单ID)；
     * 返回结果：MenuAdminNodeVo 菜单详情；
     * 简要流程：按 ID 检索菜单，验证租户归属与未删除状态后转换为 VO。
     * </p>
     *
     * @param menuId 目标菜单 ID
     * @return 菜单节点详情视图对象
     */
    @Override
    public MenuAdminNodeVo getMenuDetail(UUID menuId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null || menu.getIsdel() != 0 || !tenantId.equals(menu.getTenantId())) {
            log.warn("[查询菜单详情失败] 菜单不存在或非当前租户: menuId={}, tenantId={}", menuId, tenantId);
            throw new NotFoundException("菜单不存在或已被删除");
        }
        return convertToVo(menu);
    }

    /**
     * 创建新菜单节点。
     * <p>
     * 【用途】供管理员添加顶级菜单模块或子路由节点（写入当前租户）。
     * 主要入参：request (菜单属性集合)；
     * 返回结果：创建成功后的 MenuAdminNodeVo；
     * 简要流程：核验同租户内菜单编码唯一性与父节点合法性，绑定当前租户ID插入记录并返回详情。
     * </p>
     *
     * @param request 创建菜单请求参数
     * @return 创建后的菜单视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuAdminNodeVo createMenu(MenuCreateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        // 1. 父节点存在性及租户校验
        if (request.getParentId() != null) {
            Menu parent = menuMapper.selectById(request.getParentId());
            if (parent == null || parent.getIsdel() != 0 || !tenantId.equals(parent.getTenantId())) {
                throw new NotFoundException("指定的父级菜单不存在或已被删除");
            }
        }

        // 2. 编码唯一性冲突检查（租户隔离）
        if (menuMapper.existsByMenuCode(tenantId, request.getMenuCode().trim(), null)) {
            throw new ConflictException("该菜单编码已存在: " + request.getMenuCode().trim());
        }

        // 3. 构造菜单实体
        UUID menuId = UUID.randomUUID();
        Menu menu = new Menu();
        menu.setId(menuId);
        menu.setParentId(request.getParentId());
        menu.setTenantId(tenantId);
        menu.setMenuCode(request.getMenuCode().trim());
        menu.setMenuName(request.getMenuName().trim());
        menu.setRoutePath(request.getRoutePath());
        menu.setComponentPath(request.getComponentPath());
        menu.setIcon(request.getIcon());
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        menu.setPermissionCode(request.getPermissionCode());
        menu.setVisible(request.getVisible() != null ? request.getVisible() : true);
        menu.setStatus("ACTIVE");
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        menu.setIsdel(0);

        menuMapper.insert(menu);
        log.info("[创建菜单成功] tenantId={}, menuId={}, menuCode={}", tenantId, menuId, menu.getMenuCode());

        return getMenuDetail(menuId);
    }

    /**
     * 修改指定菜单节点的属性（含防环路校验与租户隔离）。
     * <p>
     * 【用途】供管理员调整菜单编码、名称、路由组件、图标、同级排序或调整父级层级。
     * 主要入参：menuId (目标菜单ID), request (修改字段集合)；
     * 返回结果：更新后的 MenuAdminNodeVo；
     * 简要流程：检查菜单存在性与租户归属，校验父节点非自身且非自身后代节点，更新实体并返回。
     * </p>
     *
     * @param menuId  目标菜单 ID
     * @param request 修改菜单请求参数
     * @return 更新后的菜单视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuAdminNodeVo updateMenu(UUID menuId, MenuUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null || menu.getIsdel() != 0 || !tenantId.equals(menu.getTenantId())) {
            throw new NotFoundException("菜单不存在或已被删除");
        }

        // 菜单编码修改必须执行当前租户内唯一性校验，并由实体更新真实持久化。
        String menuCode = request.getMenuCode().trim();
        if (menuMapper.existsByMenuCode(tenantId, menuCode, menuId)) {
            throw new ConflictException("该菜单编码已存在: " + menuCode);
        }

        // 防环路与父节点校验
        if (request.getParentId() != null) {
            if (request.getParentId().equals(menuId)) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "父级菜单不能设置为自身节点");
            }
            Menu parent = menuMapper.selectById(request.getParentId());
            if (parent == null || parent.getIsdel() != 0 || !tenantId.equals(parent.getTenantId())) {
                throw new NotFoundException("指定的父级菜单不存在或已被删除");
            }
            // 校验父节点不能是当前节点的后代节点
            checkDescendantCycle(tenantId, menuId, request.getParentId());
        }

        menu.setParentId(request.getParentId());
        menu.setMenuCode(menuCode);
        menu.setMenuName(request.getMenuName().trim());
        menu.setRoutePath(request.getRoutePath());
        menu.setComponentPath(request.getComponentPath());
        menu.setIcon(request.getIcon());
        if (request.getSortOrder() != null) {
            menu.setSortOrder(request.getSortOrder());
        }
        menu.setPermissionCode(request.getPermissionCode());
        if (request.getVisible() != null) {
            menu.setVisible(request.getVisible());
        }
        menu.setUpdatedAt(LocalDateTime.now());

        menuMapper.updateById(menu);
        log.info("[修改菜单成功] tenantId={}, menuId={}, menuCode={}", tenantId, menuId, menu.getMenuCode());

        return getMenuDetail(menuId);
    }

    /**
     * 快速更新菜单启用状态。
     * <p>
     * 【用途】供管理员在表格快速启用或停用菜单；visible 与 status 分别维护。
     * 主要入参：menuId (目标菜单ID), request (status 状态)；
     * 返回结果：更新后的 MenuAdminNodeVo；
     * 简要流程：校验租户归属后更新菜单 status 状态字段并落库。
     * </p>
     *
     * @param menuId  目标菜单 ID
     * @param request 启停更新请求参数
     * @return 更新后的菜单视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuAdminNodeVo updateMenuStatus(UUID menuId, MenuStatusUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null || menu.getIsdel() != 0 || !tenantId.equals(menu.getTenantId())) {
            throw new NotFoundException("菜单不存在或已被删除");
        }

        menu.setStatus(request.getStatus());
        menu.setUpdatedAt(LocalDateTime.now());
        menuMapper.updateById(menu);

        log.info("[更新菜单启用状态成功] tenantId={}, menuId={}, status={}", tenantId, menuId, request.getStatus());
        return getMenuDetail(menuId);
    }

    /**
     * 删除指定菜单节点（软删除，带依赖校验与租户隔离）。
     * <p>
     * 【用途】供管理员删除无用的废弃菜单。
     * 主要入参：menuId (目标菜单ID)；
     * 返回结果：无；
     * 简要流程：校验租户归属，检查子菜单依赖冲突（有子菜单则 409），检查角色授权依赖冲突（被角色引用则 409），逻辑软删除菜单。
     * </p>
     *
     * @param menuId 目标菜单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(UUID menuId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Menu menu = menuMapper.selectById(menuId);
        if (menu == null || menu.getIsdel() != 0 || !tenantId.equals(menu.getTenantId())) {
            throw new NotFoundException("菜单不存在或已被删除");
        }

        // 1. 子菜单依赖冲突检查 (HTTP 409)
        int childCount = menuMapper.countChildrenByParentId(tenantId, menuId);
        if (childCount > 0) {
            throw new ConflictException(String.format("该菜单下仍存在 %d 个直接子菜单，存在层级依赖冲突，禁止删除", childCount));
        }

        // 2. 角色授权依赖冲突检查 (HTTP 409)
        int roleCount = menuMapper.countAssignedRoles(tenantId, menuId);
        if (roleCount > 0) {
            throw new ConflictException(String.format("该菜单当前已被 %d 个角色授权引用，存在引用冲突，禁止删除", roleCount));
        }

        // 3. 软删除菜单
        menuMapper.deleteById(menuId);
        log.info("[删除菜单成功] tenantId={}, menuId={}, menuCode={}", tenantId, menuId, menu.getMenuCode());
    }

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
    @Override
    public List<UUID> getRoleMenuIds(UUID roleId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            throw new NotFoundException("角色不存在或不属于当前租户");
        }
        return menuMapper.findMenuIdsByRoleId(roleId);
    }

    /**
     * 校验目标父节点是否落在当前菜单节点的后代链路上，防止菜单树形成环路。
     * <p>
     * 【用途】在菜单改父级时阻断“挂到自己子孙节点下”的非法层级结构。
     * 主要入参：tenantId (当前租户ID), currentMenuId (当前菜单ID), targetParentId (拟设置的新父菜单ID)；
     * 返回结果：无；若检测到成环风险则抛出 BizException。
     * 简要流程：先查询当前租户全部菜单构建父子映射，再递归收集当前菜单的全部后代，最后判断目标父节点是否命中后代集合。
     * </p>
     *
     * @param tenantId 当前租户 ID
     * @param currentMenuId 当前菜单 ID
     * @param targetParentId 拟设置的父菜单 ID
     */
    private void checkDescendantCycle(UUID tenantId, UUID currentMenuId, UUID targetParentId) {
        List<Menu> allMenus = menuMapper.findAllMenusForAdmin(tenantId);
        Map<UUID, List<UUID>> parentToChildrenMap = new HashMap<>();
        for (Menu m : allMenus) {
            if (m.getParentId() != null) {
                parentToChildrenMap.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(m.getId());
            }
        }

        Set<UUID> descendants = new HashSet<>();
        collectDescendants(currentMenuId, parentToChildrenMap, descendants);

        if (descendants.contains(targetParentId)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "父级菜单不能设置为自身的下级子孙节点，防止形成循环层级关系");
        }
    }

    /**
     * 递归收集指定菜单节点的全部后代菜单 ID。
     * <p>
     * 【用途】为菜单层级防环校验准备完整的后代节点集合。
     * 主要入参：currentId (当前递归节点ID), parentToChildrenMap (父子映射), descendants (已收集的后代集合)；
     * 返回结果：无；结果通过 descendants 出参累积。
     * 简要流程：读取当前节点的直接子节点，逐个加入结果集合，并继续向下递归直到叶子节点。
     * </p>
     *
     * @param currentId 当前递归的菜单 ID
     * @param parentToChildrenMap 菜单父子关系映射
     * @param descendants 累积后的后代菜单 ID 集合
     */
    private void collectDescendants(UUID currentId, Map<UUID, List<UUID>> parentToChildrenMap, Set<UUID> descendants) {
        List<UUID> children = parentToChildrenMap.get(currentId);
        if (children != null) {
            for (UUID childId : children) {
                if (descendants.add(childId)) {
                    collectDescendants(childId, parentToChildrenMap, descendants);
                }
            }
        }
    }

    /**
     * 递归按排序号和创建时间对菜单树节点排序。
     * <p>
     * 【用途】保证菜单树在后台展示与前端消费时顺序稳定、可预期。
     * 主要入参：nodes (待排序的同层菜单节点列表)；
     * 返回结果：无；直接原地调整 nodes 及其子节点顺序。
     * 简要流程：先对当前层节点排序，再对子节点非空的节点继续递归排序。
     * </p>
     *
     * @param nodes 待排序的菜单节点列表
     */
    private void sortMenuNodes(List<MenuAdminNodeVo> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparing(MenuAdminNodeVo::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MenuAdminNodeVo::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        for (MenuAdminNodeVo node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortMenuNodes(node.getChildren());
            }
        }
    }

    /**
     * 将菜单实体转换为后台管理菜单节点视图对象。
     * <p>
     * 【用途】统一菜单详情与树形节点的字段输出，避免控制层重复拼装。
     * 主要入参：menu (菜单实体)；
     * 返回结果：MenuAdminNodeVo 视图对象；
     * 简要流程：逐项复制实体字段，补齐 visible 默认值，并初始化空 children 列表供后续树构建填充。
     * </p>
     *
     * @param menu 菜单实体
     * @return 菜单后台管理节点视图对象
     */
    private MenuAdminNodeVo convertToVo(Menu menu) {
        MenuAdminNodeVo vo = new MenuAdminNodeVo();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuCode(menu.getMenuCode());
        vo.setMenuName(menu.getMenuName());
        vo.setRoutePath(menu.getRoutePath());
        vo.setComponentPath(menu.getComponentPath());
        vo.setIcon(menu.getIcon());
        vo.setSortOrder(menu.getSortOrder());
        vo.setPermissionCode(menu.getPermissionCode());
        vo.setVisible(menu.getVisible() != null ? menu.getVisible() : true);
        vo.setStatus(menu.getStatus());
        vo.setCreatedAt(menu.getCreatedAt());
        vo.setUpdatedAt(menu.getUpdatedAt());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
