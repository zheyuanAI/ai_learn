package com.ailearn.platform.auth.service.impl;

import com.ailearn.platform.auth.config.JwtProperties;
import com.ailearn.platform.auth.domain.dto.LoginRequest;
import com.ailearn.platform.auth.domain.entity.Menu;
import com.ailearn.platform.auth.domain.entity.Tenant;
import com.ailearn.platform.auth.domain.entity.User;
import com.ailearn.platform.auth.domain.entity.UserSession;
import com.ailearn.platform.auth.domain.vo.LoginResponse;
import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.domain.vo.UserInfoVo;
import com.ailearn.platform.auth.domain.vo.UserProfileVo;
import com.ailearn.platform.auth.mapper.MenuMapper;
import com.ailearn.platform.auth.mapper.PermissionMapper;
import com.ailearn.platform.auth.mapper.TenantMapper;
import com.ailearn.platform.auth.mapper.UserMapper;
import com.ailearn.platform.auth.mapper.UserSessionMapper;
import com.ailearn.platform.auth.security.jwt.JwtTokenService;
import com.ailearn.platform.auth.service.AuthService;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.shared.exception.AuthException;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证与权限核心业务服务实现类。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final MenuMapper menuMapper;
    private final UserSessionMapper userSessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final SessionCacheService sessionCacheService;

    public AuthServiceImpl(TenantMapper tenantMapper,
                           UserMapper userMapper,
                           PermissionMapper permissionMapper,
                           MenuMapper menuMapper,
                           UserSessionMapper userSessionMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService,
                           JwtProperties jwtProperties,
                           SessionCacheService sessionCacheService) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.menuMapper = menuMapper;
        this.userSessionMapper = userSessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.sessionCacheService = sessionCacheService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        log.info("[登录请求处理] tenantCode={}, username={}, ip={}", request.getTenantCode(), request.getUsername(), ipAddress);

        // 0. 前置校验会话硬依赖 Redis 可用性（异常时快速失败返回 503，禁止查询数据库与签发 Token）
        if (!sessionCacheService.isRedisAvailable()) {
            log.error("[登录失败] 会话中心 Redis 探测不可用，快速失败禁止兜底: tenantCode={}, username={}",
                    request.getTenantCode(), request.getUsername());
            throw new com.ailearn.platform.shared.exception.ServiceUnavailableException("会话服务暂时不可用，请稍后重试");
        }

        // 1. 校验租户是否存在且状态正常
        Tenant tenant = tenantMapper.findByTenantCode(request.getTenantCode());
        if (tenant == null) {
            log.warn("[登录失败] 租户不存在: tenantCode={}", request.getTenantCode());
            throw new NotFoundException("指定租户不存在: " + request.getTenantCode());
        }
        if (!"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            log.warn("[登录失败] 租户已停用: tenantCode={}", request.getTenantCode());
            throw new AuthException("所属租户已停用，禁止登录");
        }

        // 2. 校验用户是否存在与密码匹配
        User user = userMapper.findByTenantIdAndUsername(tenant.getId(), request.getUsername());
        if (user == null) {
            log.warn("[登录失败] 用户不存在: tenantCode={}, username={}", request.getTenantCode(), request.getUsername());
            throw new AuthException("登录账号或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            log.warn("[登录失败] 账号已停用或锁定: username={}, status={}", request.getUsername(), user.getStatus());
            throw new AuthException("当前账号已被停用或锁定，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[登录失败] 密码错误: tenantCode={}, username={}", request.getTenantCode(), request.getUsername());
            throw new AuthException("登录账号或密码错误");
        }

        // 3. 登录时必须先从数据库计算完整权限快照，后续请求不允许因缓存未命中回源数据库。
        Set<String> permissions = permissionMapper.findPermissionCodesByUserIdAndTenantId(tenant.getId(), user.getId());
        if (permissions == null) {
            permissions = Set.of();
        }

        LocalDateTime now = LocalDateTime.now();
        Duration tokenTtl = Duration.ofSeconds(jwtProperties.getAccessTokenExpirationSeconds());
        LocalDateTime expireTime = now.plus(tokenTtl);

        // 4. 先生成不可变 JTI/JWT，JWT 只携带身份，不携带可变权限。
        String newJti = UUID.randomUUID().toString();
        String token = jwtTokenService.generateToken(user.getId(), tenant.getId(), user.getUsername(), newJti);

        // 5. 单账号单有效会话控制：废弃该用户历史活跃会话（后登顶前）
        userSessionMapper.revokeActiveSessions(tenant.getId(), user.getId(), now, "REPLACED_BY_NEW_LOGIN");

        // 6. 写入数据库会话事实记录；后续 Redis 权限写失败会抛异常并触发事务回滚。
        UserSession userSession = new UserSession(
                UUID.randomUUID(),
                tenant.getId(),
                user.getId(),
                newJti,
                "ACTIVE",
                ipAddress,
                userAgent,
                now,
                expireTime
        );
        userSessionMapper.insert(userSession);

        // 7. 严格写权限快照后再发布当前 JTI；任一缓存写入异常均不得返回 Token。
        try {
            sessionCacheService.cachePermissions(tenant.getId(), user.getId(), permissions, tokenTtl);
            sessionCacheService.saveActiveSession(tenant.getId(), user.getId(), newJti, tokenTtl);
        } catch (RuntimeException cacheWriteFailure) {
            // 修改：补偿清除可能已经写入的权限/JTI，避免“接口失败但半套授权状态仍可用”。
            cleanupFailedLoginCache(tenant.getId(), user.getId(), cacheWriteFailure);
            throw cacheWriteFailure;
        }

        // 8. 组装用户信息响应
        UserInfoVo userInfoVo = new UserInfoVo(
                user.getId(),
                tenant.getId(),
                tenant.getTenantCode(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getPhone()
        );

        log.info("[登录成功] userId={}, username={}, jti={}", user.getId(), user.getUsername(), newJti);
        return new LoginResponse(token, newJti, jwtProperties.getAccessTokenExpirationSeconds(), userInfoVo);
    }

    /**
     * 清理登录缓存双写失败产生的半成品状态。
     * 主要入参为租户、用户和原始缓存异常；无业务返回值；流程为尽力删除活跃 JTI、权限及菜单快照，
     * 清理失败作为 suppressed 异常挂到原异常上，最终仍由统一异常处理返回 503。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @param originalFailure 原始缓存写入异常
     */
    private void cleanupFailedLoginCache(UUID tenantId, UUID userId, RuntimeException originalFailure) {
        try {
            sessionCacheService.removeActiveSession(tenantId, userId);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
        try {
            sessionCacheService.evictUserAuthCache(tenantId, userId);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(UUID userId, UUID tenantId) {
        log.info("[注销登录] userId={}, tenantId={}", userId, tenantId);
        LocalDateTime now = LocalDateTime.now();

        // 1. 废弃数据库中活跃会话
        userSessionMapper.revokeActiveSessions(tenantId, userId, now, "LOGOUT");

        // 2. 清除 Redis 活跃会话与缓存
        sessionCacheService.removeActiveSession(tenantId, userId);
        sessionCacheService.evictUserAuthCache(tenantId, userId);
    }

    @Override
    public UserProfileVo getCurrentUserProfile(UUID userId, UUID tenantId) {
        // 1. 严格使用 userId + tenantId + status = ACTIVE + isdel = 0 查询，防止跨租户越权
        User user = userMapper.findByUserIdAndTenantId(userId, tenantId);
        if (user == null) {
            log.warn("[获取用户画像失败] 用户不存在或处于禁用状态或跨租户访问: userId={}, tenantId={}", userId, tenantId);
            throw new NotFoundException("当前用户不存在或已被禁用: " + userId);
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            log.warn("[获取用户画像失败] 租户不存在或已停用: tenantId={}", tenantId);
            throw new NotFoundException("所属租户不存在或已停用: " + tenantId);
        }
        String tenantCode = tenant.getTenantCode();

        // 2. 查询分配角色编码列表
        List<String> roles = userMapper.findRoleCodesByUserIdAndTenantId(tenantId, userId);

        // 3. 查询功能权限点集合（优先读 Redis 缓存）
        Set<String> perms = sessionCacheService.getCachedPermissions(tenantId, userId);
        if (perms == null) {
            // 用户画像与授权链共享同一权限快照；缺失时 Fail-Closed，不再回源数据库临时放行。
            throw new ServiceUnavailableException("权限服务暂时不可用，请稍后重试");
        }

        return new UserProfileVo(
                user.getId(),
                tenantId,
                tenantCode,
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                user.getPhone(),
                roles,
                perms
        );
    }

    @Override
    public List<MenuNodeVo> getCurrentUserMenus(UUID userId, UUID tenantId) {
        // 0. 确认用户与租户有效性
        User user = userMapper.findByUserIdAndTenantId(userId, tenantId);
        if (user == null) {
            log.warn("[获取菜单失败] 用户不存在或处于禁用状态: userId={}, tenantId={}", userId, tenantId);
            throw new NotFoundException("当前用户不存在或已被禁用: " + userId);
        }

        // 1. 优先读 Redis 缓存
        List<MenuNodeVo> cachedTree = sessionCacheService.getCachedMenus(tenantId, userId);
        if (cachedTree != null) {
            return cachedTree;
        }

        // 2. 数据库多表联查获取用户角色可访问的菜单列表
        List<Menu> menus = menuMapper.findMenusByUserIdAndTenantId(tenantId, userId);

        // 3. 构建嵌套层级菜单树
        List<MenuNodeVo> menuTree = buildMenuTree(menus);

        // 4. 写入缓存
        sessionCacheService.cacheMenus(tenantId, userId, menuTree, Duration.ofMinutes(30));

        return menuTree;
    }

    /**
     * 将平铺的菜单实体列表组装为树形嵌套结构。
     *
     * @param menuList 平铺菜单列表
     * @return 根节点嵌套树列表
     */
    private List<MenuNodeVo> buildMenuTree(List<Menu> menuList) {
        if (menuList == null || menuList.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, MenuNodeVo> nodeMap = new LinkedHashMap<>();
        for (Menu m : menuList) {
            MenuNodeVo node = new MenuNodeVo(
                    m.getId(),
                    m.getParentId(),
                    m.getMenuCode(),
                    m.getMenuName(),
                    m.getRoutePath(),
                    m.getComponentPath(),
                    m.getIcon(),
                    m.getSortOrder(),
                    m.getVisible()
            );
            node.setStatus(m.getStatus());
            nodeMap.put(m.getId(), node);
        }

        List<MenuNodeVo> rootNodes = new ArrayList<>();
        for (MenuNodeVo node : nodeMap.values()) {
            if (node.getParentId() == null || !nodeMap.containsKey(node.getParentId())) {
                rootNodes.add(node);
            } else {
                MenuNodeVo parentNode = nodeMap.get(node.getParentId());
                parentNode.addChild(node);
            }
        }

        // 按 sortOrder 排序
        Comparator<MenuNodeVo> comparator = Comparator.comparing(
                m -> m.getSortOrder() != null ? m.getSortOrder() : 0
        );
        rootNodes.sort(comparator);
        for (MenuNodeVo root : rootNodes) {
            if (root.getChildren() != null && !root.getChildren().isEmpty()) {
                root.getChildren().sort(comparator);
            }
        }

        return rootNodes;
    }
}
