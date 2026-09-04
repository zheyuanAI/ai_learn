package com.ailearn.platform.auth.service.admin.impl;

import com.ailearn.platform.auth.domain.dto.admin.RoleCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleMenusAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RolePermissionsAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleQueryRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Menu;
import com.ailearn.platform.auth.domain.entity.Permission;
import com.ailearn.platform.auth.domain.entity.Role;
import com.ailearn.platform.auth.domain.entity.RoleMenu;
import com.ailearn.platform.auth.domain.entity.RolePermission;
import com.ailearn.platform.auth.domain.vo.admin.RoleAdminVo;
import com.ailearn.platform.auth.mapper.MenuMapper;
import com.ailearn.platform.auth.mapper.PermissionMapper;
import com.ailearn.platform.auth.mapper.RoleMapper;
import com.ailearn.platform.auth.mapper.RoleMenuMapper;
import com.ailearn.platform.auth.mapper.RolePermissionMapper;
import com.ailearn.platform.auth.mapper.UserRoleMapper;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.auth.service.admin.RoleAdminService;
import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.BizException;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ValidationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 角色后台管理业务服务实现类。
 */
@Service
public class RoleAdminServiceImpl implements RoleAdminService {

    private record PermissionCacheRefreshPlan(String expectedJti) {
    }

    private static final Logger log = LoggerFactory.getLogger(RoleAdminServiceImpl.class);

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;
    private final SessionCacheService sessionCacheService;

    public RoleAdminServiceImpl(RoleMapper roleMapper,
                                PermissionMapper permissionMapper,
                                MenuMapper menuMapper,
                                RolePermissionMapper rolePermissionMapper,
                                RoleMenuMapper roleMenuMapper,
                                UserRoleMapper userRoleMapper,
                                SessionCacheService sessionCacheService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.sessionCacheService = sessionCacheService;
    }

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
    @Override
    @PreAuthorize("hasAuthority('auth:role:view')")
    public List<RoleAdminVo> listRoles(RoleQueryRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        List<Role> roles = roleMapper.selectRolesByCondition(
                tenantId,
                request.getRoleCode(),
                request.getRoleName(),
                request.getStatus()
        );

        List<RoleAdminVo> voList = new ArrayList<>();
        for (Role role : roles) {
            int userCount = roleMapper.countAssignedUsers(tenantId, role.getId());
            int permCount = roleMapper.countRolePermissions(role.getId());
            List<UUID> permIds = permissionMapper.findPermissionIdsByRoleId(role.getId());
            List<UUID> menuIds = menuMapper.findMenuIdsByRoleId(role.getId());
            voList.add(convertToVo(role, userCount, permCount, permIds, menuIds));
        }

        return voList;
    }

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
    @Override
    @PreAuthorize("hasAuthority('auth:role:view')")
    public RoleAdminVo getRoleDetail(UUID roleId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            log.warn("[查询角色失败] 角色不存在或跨租户: roleId={}, tenantId={}", roleId, tenantId);
            throw new NotFoundException("角色不存在或不属于当前租户");
        }

        int userCount = roleMapper.countAssignedUsers(tenantId, roleId);
        int permCount = roleMapper.countRolePermissions(roleId);
        List<UUID> permIds = permissionMapper.findPermissionIdsByRoleId(roleId);
        List<UUID> menuIds = menuMapper.findMenuIdsByRoleId(roleId);

        return convertToVo(role, userCount, permCount, permIds, menuIds);
    }

    /**
     * 创建新业务角色。
     * <p>
     * 【用途】供管理员在当前租户内定义新角色并绑定初始权限与菜单。
     * 主要入参：request (roleCode, roleName, description, permissionIds, menuIds)；
     * 返回结果：创建成功后的 RoleAdminVo 角色详情；
     * 简要流程：核验租户内编码唯一性，前置强校验权限点与菜单有效性及租户归属，保存角色实体并插入权限与菜单关系。
     * </p>
     *
     * @param request 角色创建请求参数
     * @return 创建后的角色管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:role:manage')")
    @Transactional(rollbackFor = Exception.class)
    public RoleAdminVo createRole(RoleCreateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        // 1. 唯一性检查
        if (roleMapper.existsByRoleCode(tenantId, request.getRoleCode().trim(), null)) {
            throw new ConflictException("该角色编码已存在: " + request.getRoleCode().trim());
        }

        // 所有关联对象先完成完整校验，避免角色或部分关联先落库。
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            validatePermissionIds(request.getPermissionIds());
        }
        if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
            validateMenuIds(tenantId, request.getMenuIds());
        }

        // 2. 插入角色
        UUID roleId = UUID.randomUUID();
        Role role = new Role();
        role.setId(roleId);
        role.setTenantId(tenantId);
        role.setRoleCode(request.getRoleCode().trim());
        role.setRoleName(request.getRoleName().trim());
        role.setDescription(request.getDescription());
        role.setStatus("ACTIVE");
        role.setCreatedBy(currentUsername != null ? currentUsername : "system");
        role.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setIsdel(0);

        roleMapper.insert(role);

        // 3. 关联权限点（强校验有效性）
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            for (UUID permId : request.getPermissionIds()) {
                rolePermissionMapper.insert(new RolePermission(UUID.randomUUID(), roleId, permId));
            }
        }

        // 4. 关联菜单（强校验租户归属与有效性）
        if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
            for (UUID menuId : request.getMenuIds()) {
                roleMenuMapper.insert(new RoleMenu(UUID.randomUUID(), roleId, menuId));
            }
        }

        log.info("[创建角色成功] roleId={}, roleCode={}, tenantId={}", roleId, role.getRoleCode(), tenantId);
        return getRoleDetail(roleId);
    }

    /**
     * 修改角色展示信息与授权。
     * <p>
     * 【用途】供管理员修改角色名称、描述或重新全量指派权限与菜单。
     * 主要入参：roleId (目标角色ID), request (修改参数)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：核验租户隔离与基础角色保护，前置强校验权限与菜单有效性，更新角色字段，重置权限与菜单关联，清理受影响用户的 Redis 权限缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 角色修改请求参数
     * @return 更新后的角色管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:role:manage')")
    @Transactional(rollbackFor = Exception.class)
    public RoleAdminVo updateRole(UUID roleId, RoleUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            throw new NotFoundException("角色不存在或不属于当前租户");
        }

        // 先校验全部新授权 ID，再更新角色字段或删除旧关联。
        if (request.getPermissionIds() != null) {
            validatePermissionIds(request.getPermissionIds());
        }
        if (request.getMenuIds() != null) {
            validateMenuIds(tenantId, request.getMenuIds());
        }

        // 所有新授权校验完成后，紧邻数据库关系变更前失效旧权限快照。
        Map<UUID, PermissionCacheRefreshPlan> cacheRefresh = prepareRolePermissionCacheRefresh(tenantId, roleId);

        role.setRoleName(request.getRoleName().trim());
        role.setDescription(request.getDescription());
        role.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        // 重新分配权限点（前置强校验）
        if (request.getPermissionIds() != null) {
            rolePermissionMapper.deleteByRoleId(roleId);
            for (UUID permId : request.getPermissionIds()) {
                rolePermissionMapper.insert(new RolePermission(UUID.randomUUID(), roleId, permId));
            }
        }

        // 重新分配菜单（前置强校验）
        if (request.getMenuIds() != null) {
            roleMenuMapper.deleteByRoleId(roleId);
            for (UUID menuId : request.getMenuIds()) {
                roleMenuMapper.insert(new RoleMenu(UUID.randomUUID(), roleId, menuId));
            }
        }

        // 数据库关系变更完成后按原活跃会话 TTL 重建权限快照；失败则保持缺失并向上返回 503。
        rebuildRolePermissionCaches(tenantId, cacheRefresh);

        log.info("[修改角色成功] roleId={}, roleCode={}, tenantId={}", roleId, role.getRoleCode(), tenantId);
        return getRoleDetail(roleId);
    }

    /**
     * 变更角色的启用状态（正常/禁用）。
     * <p>
     * 【用途】供管理员启停用特定业务角色。
     * 主要入参：roleId (目标角色ID), request (目标状态 ACTIVE/DISABLED)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：禁止停用系统预置管理员角色，更新状态并清理受影响用户的权限缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 状态变更请求参数
     * @return 更新后的角色管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:role:manage')")
    @Transactional(rollbackFor = Exception.class)
    public RoleAdminVo updateRoleStatus(UUID roleId, RoleStatusUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            throw new NotFoundException("角色不存在或不属于当前租户");
        }

        if (isProtectedAdminRole(role.getRoleCode()) && "DISABLED".equalsIgnoreCase(request.getStatus())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "禁止停用系统预置管理员角色");
        }

        // 保护校验通过后再失效受影响用户的旧权限快照，避免拒绝请求产生缓存副作用。
        Map<UUID, PermissionCacheRefreshPlan> cacheRefresh = prepareRolePermissionCacheRefresh(tenantId, roleId);

        role.setStatus(request.getStatus());
        role.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        rebuildRolePermissionCaches(tenantId, cacheRefresh);

        log.info("[更新角色状态成功] roleId={}, status={}, tenantId={}", roleId, request.getStatus(), tenantId);
        return getRoleDetail(roleId);
    }

    /**
     * 为指定角色全量分配功能权限点。
     * <p>
     * 【用途】供管理员在角色授权界面批量保存权限勾选结果。
     * 主要入参：roleId (目标角色ID), request (权限点ID列表)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：核验租户隔离与权限点有效性强校验，事务化全量替换 role_permission 关联记录，批量清除所有属于该角色的用户权限缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 权限分配请求参数
     * @return 更新后的角色管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:role:manage')")
    @Transactional(rollbackFor = Exception.class)
    public RoleAdminVo assignPermissions(UUID roleId, RolePermissionsAssignRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            throw new NotFoundException("角色不存在或不属于当前租户");
        }

        if (request.getPermissionIds() != null) {
            validatePermissionIds(request.getPermissionIds());
        }

        // 权限 ID 全量校验通过后再失效受影响用户的旧权限快照。
        Map<UUID, PermissionCacheRefreshPlan> cacheRefresh = prepareRolePermissionCacheRefresh(tenantId, roleId);

        rolePermissionMapper.deleteByRoleId(roleId);
        if (request.getPermissionIds() != null) {
            for (UUID permId : request.getPermissionIds()) {
                rolePermissionMapper.insert(new RolePermission(UUID.randomUUID(), roleId, permId));
            }
        }

        rebuildRolePermissionCaches(tenantId, cacheRefresh);
        log.info("[分配角色权限成功] roleId={}, permCount={}, tenantId={}", roleId, request.getPermissionIds() != null ? request.getPermissionIds().size() : 0, tenantId);
        return getRoleDetail(roleId);
    }

    /**
     * 为指定角色全量分配动态菜单。
     * <p>
     * 【用途】供管理员在角色授权界面批量保存菜单勾选树。
     * 主要入参：roleId (目标角色ID), request (菜单ID列表)；
     * 返回结果：更新后的 RoleAdminVo；
     * 简要流程：核验租户隔离与菜单有效性强校验，事务化全量替换 role_menu 关联记录，批量清除所有属于该角色的用户菜单缓存。
     * </p>
     *
     * @param roleId  目标角色 ID
     * @param request 菜单分配请求参数
     * @return 更新后的角色管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:role:manage')")
    @Transactional(rollbackFor = Exception.class)
    public RoleAdminVo assignMenus(UUID roleId, RoleMenusAssignRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            throw new NotFoundException("角色不存在或不属于当前租户");
        }

        if (request.getMenuIds() != null) {
            validateMenuIds(tenantId, request.getMenuIds());
        }

        // 菜单 ID 全量校验通过后再失效受影响用户的旧权限快照。
        Map<UUID, PermissionCacheRefreshPlan> cacheRefresh = prepareRolePermissionCacheRefresh(tenantId, roleId);

        roleMenuMapper.deleteByRoleId(roleId);
        if (request.getMenuIds() != null) {
            for (UUID menuId : request.getMenuIds()) {
                roleMenuMapper.insert(new RoleMenu(UUID.randomUUID(), roleId, menuId));
            }
        }

        rebuildRolePermissionCaches(tenantId, cacheRefresh);
        log.info("[分配角色菜单成功] roleId={}, menuCount={}, tenantId={}", roleId, request.getMenuIds() != null ? request.getMenuIds().size() : 0, tenantId);
        return getRoleDetail(roleId);
    }

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
    @Override
    @PreAuthorize("hasAuthority('auth:role:manage')")
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(UUID roleId) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            throw new NotFoundException("角色不存在或不属于当前租户");
        }

        // 1. 基础预置管理员保护
        if (isProtectedAdminRole(role.getRoleCode())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "系统预置管理员角色禁止删除");
        }

        // 2. 关联用户冲突检查 (HTTP 409)
        int assignedUsers = roleMapper.countAssignedUsers(tenantId, roleId);
        if (assignedUsers > 0) {
            throw new ConflictException(String.format("该角色当前已分配给 %d 名用户，存在引用冲突，禁止删除", assignedUsers));
        }

        // 3. 软删除角色与关联清理
        roleMapper.deleteById(roleId);
        rolePermissionMapper.deleteByRoleId(roleId);
        roleMenuMapper.deleteByRoleId(roleId);
        userRoleMapper.deleteByRoleIdAndTenantId(tenantId, roleId);

        log.info("[删除角色成功] roleId={}, roleCode={}, tenantId={}", roleId, role.getRoleCode(), tenantId);
    }

    /**
     * 校验待分配权限点 ID 集合的有效性。
     * <p>
     * 【新增方法】执行全量权限 ID 前置合法性校验。
     * 主要入参：permissionIds (权限点ID集合)；
     * 返回结果：无；若存在无效或已删除 ID 则抛出 ValidationException。
     * </p>
     *
     * @param permissionIds 权限 ID 列表
     */
    private void validatePermissionIds(List<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (UUID permId : permissionIds) {
            if (permId == null) {
                throw new ValidationException("权限ID不能为空");
            }
            Permission permission = permissionMapper.selectById(permId);
            if (permission == null || permission.getIsdel() != 0) {
                throw new ValidationException("包含无效或已删除的权限ID: " + permId);
            }
        }
    }

    /**
     * 校验待分配菜单 ID 集合的有效性与租户归属。
     * <p>
     * 【新增方法】执行全量菜单 ID 前置合法性校验与租户隔离校验。
     * 主要入参：tenantId (租户ID), menuIds (菜单ID集合)；
     * 返回结果：无；若存在无效、已删除或非当前租户 ID 则抛出 ValidationException。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param menuIds  菜单 ID 列表
     */
    private void validateMenuIds(UUID tenantId, List<UUID> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        for (UUID menuId : menuIds) {
            if (menuId == null) {
                throw new ValidationException("菜单ID不能为空");
            }
        }
        if (new HashSet<>(menuIds).size() != menuIds.size()) {
            throw new ValidationException("菜单ID不能重复");
        }

        // 单条 SQL 完成存在性、当前租户、未删除及 ACTIVE 状态的整体校验。
        List<Menu> validMenus = menuMapper.findActiveMenusByIdsAndTenantId(tenantId, menuIds);
        Set<UUID> validMenuIds = new HashSet<>();
        for (Menu menu : validMenus) {
            validMenuIds.add(menu.getId());
        }
        for (UUID menuId : menuIds) {
            if (!validMenuIds.contains(menuId)) {
                throw new ValidationException("包含无效、已删除、停用或非当前租户的菜单ID: " + menuId);
            }
        }
    }

    /**
     * 判断是否为系统预置管理员角色。
     */
    private boolean isProtectedAdminRole(String roleCode) {
        return "tenant.admin".equalsIgnoreCase(roleCode) || "TENANT_ADMIN".equalsIgnoreCase(roleCode);
    }

    /**
     * 在角色授权变更前收集受影响用户的会话 TTL 并删除旧权限/菜单快照。
     * 主要入参为租户与角色 ID；返回用户到剩余会话 TTL 的映射，供变更完成后重建权限快照。
     *
     * @param tenantId 租户 ID
     * @param roleId 角色 ID
     * @return 受影响用户及其变更前活跃会话 JTI；无活跃会话的用户值为 null
     */
    private Map<UUID, PermissionCacheRefreshPlan> prepareRolePermissionCacheRefresh(UUID tenantId, UUID roleId) {
        List<UUID> userIds = userRoleMapper.findUserIdsByRoleIdAndTenantId(tenantId, roleId);
        Map<UUID, PermissionCacheRefreshPlan> refreshPlan = new LinkedHashMap<>();
        if (userIds == null) {
            return refreshPlan;
        }
        for (UUID userId : userIds) {
            String expectedJti = sessionCacheService.getActiveSessionJti(tenantId, userId);
            sessionCacheService.evictUserAuthCache(tenantId, userId);
            refreshPlan.put(userId, new PermissionCacheRefreshPlan(expectedJti));
        }
        return refreshPlan;
    }

    /**
     * 注册角色授权变更的事务提交后权限快照重建任务。
     * 主要入参为租户 ID 与变更前刷新计划；无活跃会话时只保留缓存缺失，避免制造脱离会话的授权状态。
     * 简要流程：事务提交后逐用户确认 JTI 未被并发登录替换，再读取当前 TTL 与最新权限码写入缓存。
     *
     * @param tenantId 租户 ID
     * @param refreshPlan 用户及其原活跃会话 JTI
     */
    private void rebuildRolePermissionCaches(UUID tenantId,
                                             Map<UUID, PermissionCacheRefreshPlan> refreshPlan) {
        if (refreshPlan == null || refreshPlan.isEmpty()) {
            return;
        }
        Runnable refresh = () -> refreshRolePermissionCachesAfterCommit(tenantId, refreshPlan);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refresh.run();
                }
            });
        } else {
            refresh.run();
        }
    }

    /**
     * 在角色授权事务提交后按最新数据库关系重建受影响用户权限缓存。
     * 主要入参为租户 ID 与用户刷新计划；无返回值；流程为逐用户确认 JTI、读取 TTL、查询权限并写缓存。
     * 并发登录已经替换 JTI 时跳过旧任务，防止旧快照覆盖新会话。
     *
     * @param tenantId 租户 ID
     * @param refreshPlan 用户及预期 JTI 映射
     */
    private void refreshRolePermissionCachesAfterCommit(
            UUID tenantId,
            Map<UUID, PermissionCacheRefreshPlan> refreshPlan) {
        for (Map.Entry<UUID, PermissionCacheRefreshPlan> entry : refreshPlan.entrySet()) {
            PermissionCacheRefreshPlan plan = entry.getValue();
            if (plan == null || plan.expectedJti() == null) {
                continue;
            }
            String currentJti = sessionCacheService.getActiveSessionJti(tenantId, entry.getKey());
            if (!plan.expectedJti().equals(currentJti)) {
                continue;
            }
            java.time.Duration ttl = sessionCacheService.getActiveSessionTtl(tenantId, entry.getKey());
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                continue;
            }
            Set<String> permissions = permissionMapper.findPermissionCodesByUserIdAndTenantId(
                    tenantId, entry.getKey());
            sessionCacheService.cachePermissions(
                    tenantId, entry.getKey(), permissions != null ? permissions : Set.of(), ttl);
        }
    }

    /**
     * 实体转 VO 转换方法。
     */
    private RoleAdminVo convertToVo(Role role, int userCount, int permCount, List<UUID> permIds, List<UUID> menuIds) {
        RoleAdminVo vo = new RoleAdminVo();
        vo.setId(role.getId());
        vo.setTenantId(role.getTenantId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setUserCount(userCount);
        vo.setPermissionCount(permCount);
        vo.setPermissionIds(permIds);
        vo.setMenuIds(menuIds);
        vo.setCreatedBy(role.getCreatedBy());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedBy(role.getUpdatedBy());
        vo.setUpdatedAt(role.getUpdatedAt());
        return vo;
    }
}
