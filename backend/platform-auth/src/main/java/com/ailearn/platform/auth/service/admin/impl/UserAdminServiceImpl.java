package com.ailearn.platform.auth.service.admin.impl;

import com.ailearn.platform.auth.domain.dto.admin.UserCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserPageQueryRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserResetPasswordRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserRoleAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Role;
import com.ailearn.platform.auth.domain.entity.User;
import com.ailearn.platform.auth.domain.entity.UserRole;
import com.ailearn.platform.auth.domain.vo.admin.PageResult;
import com.ailearn.platform.auth.domain.vo.admin.UserAdminVo;
import com.ailearn.platform.auth.mapper.RoleMapper;
import com.ailearn.platform.auth.mapper.PermissionMapper;
import com.ailearn.platform.auth.mapper.UserMapper;
import com.ailearn.platform.auth.mapper.UserRoleMapper;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.auth.service.admin.UserAdminService;
import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.BizException;
import com.ailearn.platform.shared.exception.ConflictException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ValidationException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 用户后台管理业务服务实现类。
 */
@Service
public class UserAdminServiceImpl implements UserAdminService {

    private record PermissionCacheRefreshPlan(String expectedJti) {
    }

    private static final Logger log = LoggerFactory.getLogger(UserAdminServiceImpl.class);

    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionCacheService sessionCacheService;

    public UserAdminServiceImpl(UserMapper userMapper,
                                PermissionMapper permissionMapper,
                                RoleMapper roleMapper,
                                UserRoleMapper userRoleMapper,
                                PasswordEncoder passwordEncoder,
                                SessionCacheService sessionCacheService) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionCacheService = sessionCacheService;
    }

    /**
     * 多条件动态分页查询当前租户内的用户列表。
     * <p>
     * 【用途】供管理后台用户表格分页展示与模糊检索。
     * 主要入参：request (分页与筛选参数)；
     * 返回结果：PageResult&lt;UserAdminVo&gt; 用户分页结果；
     * 简要流程：提取租户 ID，执行分页多条件联查，批量装配角色明细后返回。
     * </p>
     *
     * @param request 分页检索请求参数
     * @return 分页结果包装对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:view')")
    public PageResult<UserAdminVo> pageUsers(UserPageQueryRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Page<User> page = new Page<>(request.getPage(), request.getSize());
        IPage<User> userPage = userMapper.selectUserPage(page, tenantId, request);

        List<UserAdminVo> voList = new ArrayList<>();
        for (User user : userPage.getRecords()) {
            List<Role> roles = userMapper.findRolesByUserIdAndTenantId(tenantId, user.getId());
            voList.add(convertToVo(user, roles));
        }

        return PageResult.of(userPage.getCurrent(), userPage.getSize(), userPage.getTotal(), voList);
    }

    /**
     * 查询指定用户的详细信息。
     * <p>
     * 【用途】供管理后台查看或编辑特定用户基本画像与分配的角色。
     * 主要入参：userId (目标用户ID)；
     * 返回结果：UserAdminVo 用户详情；
     * 简要流程：核验租户隔离，查询用户实体与关联的角色清单，组装 VO 返回。
     * </p>
     *
     * @param userId 目标用户唯一标识 ID
     * @return 用户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:view')")
    public UserAdminVo getUserDetail(UUID userId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user == null || user.getIsdel() != 0) {
            log.warn("[查询用户详情失败] 用户不存在或跨租户: userId={}, tenantId={}", userId, tenantId);
            throw new NotFoundException("用户不存在或不属于当前租户");
        }

        List<Role> roles = userMapper.findRolesByUserIdAndTenantId(tenantId, userId);
        return convertToVo(user, roles);
    }

    /**
     * 创建新用户账号。
     * <p>
     * 【用途】供管理员录入新员工账号、初始密码并分配初始角色。
     * 主要入参：request (用户基础信息与角色ID列表)；
     * 返回结果：创建成功后的 UserAdminVo；
     * 简要流程：核验用户名与工号在租户内的唯一性，采用 BCrypt 强哈希加密密码，保存用户及用户角色关联。
     * </p>
     *
     * @param request 用户创建请求参数
     * @return 创建后的用户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:manage')")
    @Transactional(rollbackFor = Exception.class)
    public UserAdminVo createUser(UserCreateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        // 1. 唯一性冲突检查
        if (userMapper.existsByUsername(tenantId, request.getUsername().trim(), null)) {
            throw new ConflictException("该登录账号已存在: " + request.getUsername().trim());
        }
        if (request.getUserNo() != null && !request.getUserNo().trim().isEmpty()) {
            if (userMapper.existsByUserNo(tenantId, request.getUserNo().trim(), null)) {
                throw new ConflictException("该员工工号已存在: " + request.getUserNo().trim());
            }
        }

        // 角色关联写入前一次性校验全部角色，失败时不创建用户也不产生部分关联。
        List<Role> validatedRoles = validateRoleIds(tenantId, request.getRoleIds());

        // 2. 构造用户实体
        UUID newUserId = UUID.randomUUID();
        String userNo = (request.getUserNo() != null && !request.getUserNo().trim().isEmpty())
                ? request.getUserNo().trim()
                : request.getUsername().trim();

        User user = new User();
        user.setId(newUserId);
        user.setTenantId(tenantId);
        user.setUserNo(userNo);
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
        user.setRealName(request.getRealName().trim());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        user.setCreatedBy(currentUsername != null ? currentUsername : "system");
        user.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setIsdel(0);

        userMapper.insert(user);

        // 3. 关联初始角色（前置批量校验后再写入）
        if (!validatedRoles.isEmpty()) {
            for (Role role : validatedRoles) {
                UUID roleId = role.getId();
                UserRole userRole = new UserRole(UUID.randomUUID(), tenantId, newUserId, roleId);
                userRoleMapper.insert(userRole);
            }
        }

        log.info("[创建用户成功] userId={}, username={}, tenantId={}", newUserId, user.getUsername(), tenantId);
        return getUserDetail(newUserId);
    }

    /**
     * 修改用户基本信息与角色。
     * <p>
     * 【用途】供管理员更新用户的真实姓名、工号、联系方式及重新指派角色。
     * 主要入参：userId (目标用户ID), request (修改字段集合)；
     * 返回结果：更新后的 UserAdminVo；
     * 简要流程：核验租户与工号唯一性，前置强校验角色有效性，更新用户基本字段，全量替换角色并清理受影响缓存。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 用户修改请求参数
     * @return 更新后的用户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:manage')")
    @Transactional(rollbackFor = Exception.class)
    public UserAdminVo updateUser(UUID userId, UserUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user == null || user.getIsdel() != 0) {
            throw new NotFoundException("用户不存在或不属于当前租户");
        }

        // 角色授权校验必须先于用户字段更新、旧关联删除及新关联写入。
        List<Role> validatedRoles = request.getRoleIds() != null
                ? validateRoleIds(tenantId, request.getRoleIds())
                : List.of();
        if (request.getRoleIds() != null) {
            checkLastAdminRoleRemoval(tenantId, userId, validatedRoles);
        }

        // 工号唯一性校验
        if (request.getUserNo() != null && !request.getUserNo().trim().isEmpty()) {
            if (userMapper.existsByUserNo(tenantId, request.getUserNo().trim(), userId)) {
                throw new ConflictException("该员工工号已被其他用户占用: " + request.getUserNo().trim());
            }
            user.setUserNo(request.getUserNo().trim());
        }

        // 所有请求校验完成后，紧邻角色关系变更前失效旧授权快照；失败请求不会无谓清空缓存。
        PermissionCacheRefreshPlan permissionCacheRefreshPlan = request.getRoleIds() != null
                ? preparePermissionCacheRefresh(tenantId, userId)
                : null;

        user.setRealName(request.getRealName().trim());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 若传入了角色 ID 列表，则执行强校验并全量替换角色
        if (request.getRoleIds() != null) {
            userRoleMapper.deleteByUserIdAndTenantId(tenantId, userId);
            for (Role role : validatedRoles) {
                UUID roleId = role.getId();
                UserRole userRole = new UserRole(UUID.randomUUID(), tenantId, userId, roleId);
                userRoleMapper.insert(userRole);
            }
            // 角色关系提交后按原会话剩余 TTL 重建权限快照；缓存失败保持 Fail-Closed。
            rebuildPermissionCache(tenantId, userId, permissionCacheRefreshPlan);
        }

        log.info("[修改用户成功] userId={}, username={}, tenantId={}", userId, user.getUsername(), tenantId);
        return getUserDetail(userId);
    }

    /**
     * 变更用户账号状态（正常/禁用/锁定）。
     * <p>
     * 【用途】供管理员启停用或锁定用户账号，具备自保护与最后管理员保护。
     * 主要入参：userId (目标用户ID), request (目标状态 ACTIVE/DISABLED/LOCKED)；
     * 返回结果：更新后的 UserAdminVo；
     * 简要流程：阻断停用自身账号，阻断停用租户内最后一个活跃管理员，更新状态并剔除下线。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 状态变更请求参数
     * @return 更新后的用户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:manage')")
    @Transactional(rollbackFor = Exception.class)
    public UserAdminVo updateUserStatus(UUID userId, UserStatusUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID currentUserId = UserContextHolder.getUserId();
        String currentUsername = UserContextHolder.getUsername();

        // 1. 防自停用/锁定保护
        if (currentUserId != null && currentUserId.equals(userId) && !"ACTIVE".equalsIgnoreCase(request.getStatus())) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "禁止停用或锁定当前登录自身账号");
        }

        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user == null || user.getIsdel() != 0) {
            throw new NotFoundException("用户不存在或不属于当前租户");
        }

        // 2. 最后活跃管理员停用保护
        if ("ACTIVE".equalsIgnoreCase(user.getStatus()) && !"ACTIVE".equalsIgnoreCase(request.getStatus())) {
            if (isUserActiveAdmin(tenantId, userId)) {
                int activeAdmins = userMapper.countActiveAdmins(tenantId);
                if (activeAdmins <= 1) {
                    throw new BizException(CommonErrorCode.BAD_REQUEST, "禁止停用或锁定租户内最后一个处于正常状态的管理员账号");
                }
            }
        }

        // 停用前先撤销会话与授权快照，避免状态变更窗口继续接受旧授权。
        if (!"ACTIVE".equalsIgnoreCase(request.getStatus())) {
            sessionCacheService.removeActiveSession(tenantId, userId);
            sessionCacheService.evictUserAuthCache(tenantId, userId);
        }

        user.setStatus(request.getStatus());
        user.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 3. 状态非正常时强制下线并清理缓存
        log.info("[更新用户状态成功] userId={}, status={}, tenantId={}", userId, request.getStatus(), tenantId);
        return getUserDetail(userId);
    }

    /**
     * 重置指定用户的登录密码。
     * <p>
     * 【用途】供管理员对忘记密码或密码泄露的用户执行强制密码重置。
     * 主要入参：userId (目标用户ID), request (新密码明文)；
     * 返回结果：无；
     * 简要流程：核验租户隔离，BCrypt 哈希新密码并落库，清除活跃会话以强制重新登录。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 重置密码请求参数
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:manage')")
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UUID userId, UserResetPasswordRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user == null || user.getIsdel() != 0) {
            throw new NotFoundException("用户不存在或不属于当前租户");
        }

        // 密码变更会强制下线，先删除在线会话和授权快照，再落库新密码。
        sessionCacheService.removeActiveSession(tenantId, userId);
        sessionCacheService.evictUserAuthCache(tenantId, userId);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("[重置密码成功] userId={}, tenantId={}", userId, tenantId);
    }

    /**
     * 为指定用户重新分配所属角色列表。
     * <p>
     * 【用途】供管理员调整用户的角色授权。
     * 主要入参：userId (目标用户ID), request (目标角色ID列表)；
     * 返回结果：更新后的 UserAdminVo；
     * 简要流程：前置全量角色合法性强校验、租户隔离与最后管理员保护，全量替换 user_role 关系，清理缓存。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 角色分配请求参数
     * @return 更新后的用户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:manage')")
    @Transactional(rollbackFor = Exception.class)
    public UserAdminVo assignRoles(UUID userId, UserRoleAssignRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user == null || user.getIsdel() != 0) {
            throw new NotFoundException("用户不存在或不属于当前租户");
        }

        // 在删除旧关联前一次性校验全部角色的租户、逻辑删除和启用状态。
        List<Role> validatedRoles = request.getRoleIds() != null
                ? validateRoleIds(tenantId, request.getRoleIds())
                : List.of();
        checkLastAdminRoleRemoval(tenantId, userId, validatedRoles);
        PermissionCacheRefreshPlan permissionCacheRefreshPlan = preparePermissionCacheRefresh(tenantId, userId);

        userRoleMapper.deleteByUserIdAndTenantId(tenantId, userId);
        if (request.getRoleIds() != null) {
            for (Role role : validatedRoles) {
                UUID roleId = role.getId();
                UserRole userRole = new UserRole(UUID.randomUUID(), tenantId, userId, roleId);
                userRoleMapper.insert(userRole);
            }
        }

        rebuildPermissionCache(tenantId, userId, permissionCacheRefreshPlan);
        log.info("[分配角色成功] userId={}, roleCount={}, tenantId={}", userId, request.getRoleIds() != null ? request.getRoleIds().size() : 0, tenantId);
        return getUserDetail(userId);
    }

    /**
     * 删除指定用户账号（软删除）。
     * <p>
     * 【用途】供管理员移除账号。
     * 主要入参：userId (目标用户ID)；
     * 返回结果：无；
     * 简要流程：阻断删除自身账号，阻断删除租户内最后一个活跃管理员，执行软删除，清理关系与会话。
     * </p>
     *
     * @param userId 目标用户 ID
     */
    @Override
    @PreAuthorize("hasAuthority('auth:user:manage')")
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(UUID userId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        UUID currentUserId = UserContextHolder.getUserId();

        // 1. 防自删除保护
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new BizException(CommonErrorCode.BAD_REQUEST, "禁止删除当前登录自身账号");
        }

        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user == null || user.getIsdel() != 0) {
            throw new NotFoundException("用户不存在或不属于当前租户");
        }

        // 2. 最后活跃管理员删除保护
        if ("ACTIVE".equalsIgnoreCase(user.getStatus()) && isUserActiveAdmin(tenantId, userId)) {
            int activeAdmins = userMapper.countActiveAdmins(tenantId);
            if (activeAdmins <= 1) {
                throw new BizException(CommonErrorCode.BAD_REQUEST, "禁止删除租户内最后一个处于正常状态的管理员账号");
            }
        }

        // 所有删除保护通过后再撤销在线会话和权限快照，避免被拒绝的删除请求产生副作用。
        sessionCacheService.removeActiveSession(tenantId, userId);
        sessionCacheService.evictUserAuthCache(tenantId, userId);

        // 3. 软删除用户并清理角色关系与会话缓存
        userMapper.deleteById(userId);
        userRoleMapper.deleteByUserIdAndTenantId(tenantId, userId);
        log.info("[删除用户成功] userId={}, username={}, tenantId={}", userId, user.getUsername(), tenantId);
    }

    /**
     * 校验待分配角色 ID 集合的有效性与租户归属。
     * <p>
     * 【新增方法】执行全量角色 ID 前置校验，杜绝部分生效或容忍脏 ID。
     * 主要入参：tenantId (租户ID), roleIds (角色ID集合)；
     * 返回结果：无；若校验失败抛出 ValidationException。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param roleIds  待校验的角色 ID 列表
     */
    private List<Role> validateRoleIds(UUID tenantId, List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        for (UUID roleId : roleIds) {
            if (roleId == null) {
                throw new ValidationException("角色ID不能为空");
            }
        }
        if (new HashSet<>(roleIds).size() != roleIds.size()) {
            throw new ValidationException("角色ID不能重复");
        }

        // 单条 SQL 完成存在性、当前租户、未删除及 ACTIVE 状态的整体校验。
        List<Role> validRoles = roleMapper.findActiveRolesByIdsAndTenantId(tenantId, roleIds);
        Set<UUID> validRoleIds = new HashSet<>();
        for (Role role : validRoles) {
            validRoleIds.add(role.getId());
        }
        for (UUID roleId : roleIds) {
            if (!validRoleIds.contains(roleId)) {
                throw new ValidationException("包含无效、已删除、停用或非当前租户的角色ID: " + roleId);
            }
        }
        return validRoles;
    }

    /**
     * 判断指定用户是否当前持有正常状态的管理员角色。
     */
    private boolean isUserActiveAdmin(UUID tenantId, UUID userId) {
        List<String> roleCodes = userMapper.findRoleCodesByUserIdAndTenantId(tenantId, userId);
        if (roleCodes == null) return false;
        return roleCodes.contains("tenant.admin") || roleCodes.contains("TENANT_ADMIN");
    }

    /**
     * 检查是否正在尝试移除租户内最后一个活跃管理员的管理员身份。
     */
    private void checkLastAdminRoleRemoval(UUID tenantId, UUID userId, List<Role> newRoles) {
        User user = userMapper.findAnyStatusUserByIdAndTenantId(userId, tenantId);
        if (user != null && "ACTIVE".equalsIgnoreCase(user.getStatus()) && isUserActiveAdmin(tenantId, userId)) {
            // 检查新角色列表中是否仍然包含管理员角色
            boolean stillAdmin = false;
            if (newRoles != null) {
                for (Role role : newRoles) {
                    if ("tenant.admin".equalsIgnoreCase(role.getRoleCode()) || "TENANT_ADMIN".equalsIgnoreCase(role.getRoleCode())) {
                        stillAdmin = true;
                        break;
                    }
                }
            }
            if (!stillAdmin) {
                int activeAdmins = userMapper.countActiveAdmins(tenantId);
                if (activeAdmins <= 1) {
                    throw new BizException(CommonErrorCode.BAD_REQUEST, "无法移除租户内最后一个处于正常状态的管理员角色");
                }
            }
        }
    }

    /**
     * 在用户角色授权变更前保存当前活跃会话剩余 TTL，并清除旧权限菜单快照。
     * 主要入参为租户 ID 与用户 ID；返回该会话可复用的剩余 TTL；无活跃会话时返回 null。
     * 简要流程：先读取会话 TTL，再失效旧授权缓存，确保后续重建不会丢失会话生命周期。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 权限缓存刷新计划；无活跃会话时返回空计划
     */
    private PermissionCacheRefreshPlan preparePermissionCacheRefresh(UUID tenantId, UUID userId) {
        String expectedJti = sessionCacheService.getActiveSessionJti(tenantId, userId);
        sessionCacheService.evictUserAuthCache(tenantId, userId);
        return new PermissionCacheRefreshPlan(expectedJti);
    }

    /**
     * 注册用户权限快照的事务提交后重建任务。
     * 主要入参为租户 ID、用户 ID 与变更前的 JTI；无活跃会话时不注册任务。
     * 简要流程：事务提交后再次确认 JTI 未被并发登录替换，读取当前 TTL 与最新权限码并写入缓存；
     * 缓存故障直接抛出，保持 Fail-Closed。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param refreshPlan 变更前活跃会话刷新计划
     */
    private void rebuildPermissionCache(UUID tenantId,
                                        UUID userId,
                                        PermissionCacheRefreshPlan refreshPlan) {
        if (refreshPlan == null || refreshPlan.expectedJti() == null) {
            return;
        }
        Runnable refresh = () -> refreshPermissionCacheAfterCommit(tenantId, userId, refreshPlan.expectedJti());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refresh.run();
                }
            });
        } else {
            // 无事务调用仅用于离线测试或特殊宿主，仍沿用同一 JTI/TTL 校验。
            refresh.run();
        }
    }

    /**
     * 在数据库事务提交后刷新用户权限快照。
     * 主要入参为租户、用户和预期 JTI；无返回值；流程为确认当前 JTI、读取剩余 TTL、查询最新权限并写入缓存。
     * 并发登录已替换 JTI 时放弃旧刷新任务，避免覆盖新会话的授权快照。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param expectedJti 变更前活跃会话 JTI
     */
    private void refreshPermissionCacheAfterCommit(UUID tenantId, UUID userId, String expectedJti) {
        String currentJti = sessionCacheService.getActiveSessionJti(tenantId, userId);
        if (!expectedJti.equals(currentJti)) {
            return;
        }
        java.time.Duration ttl = sessionCacheService.getActiveSessionTtl(tenantId, userId);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        Set<String> permissions = permissionMapper.findPermissionCodesByUserIdAndTenantId(tenantId, userId);
        sessionCacheService.cachePermissions(tenantId, userId, permissions != null ? permissions : Set.of(), ttl);
    }

    /**
     * 实体转 VO 转换方法（严格安全脱敏，无密码哈希）。
     */
    private UserAdminVo convertToVo(User user, List<Role> roles) {
        UserAdminVo vo = new UserAdminVo();
        vo.setId(user.getId());
        vo.setTenantId(user.getTenantId());
        vo.setUserNo(user.getUserNo());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreatedBy(user.getCreatedBy());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedBy(user.getUpdatedBy());
        vo.setUpdatedAt(user.getUpdatedAt());

        List<UserAdminVo.UserRoleItemVo> roleItems = new ArrayList<>();
        List<UUID> roleIds = new ArrayList<>();
        if (roles != null) {
            for (Role role : roles) {
                roleItems.add(new UserAdminVo.UserRoleItemVo(role.getId(), role.getRoleCode(), role.getRoleName()));
                roleIds.add(role.getId());
            }
        }
        vo.setRoles(roleItems);
        vo.setRoleIds(roleIds);
        return vo;
    }
}
