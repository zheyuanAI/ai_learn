package com.ailearn.platform.auth.service.admin.impl;

import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Tenant;
import com.ailearn.platform.auth.domain.entity.User;
import com.ailearn.platform.auth.domain.vo.admin.TenantAdminVo;
import com.ailearn.platform.auth.mapper.TenantMapper;
import com.ailearn.platform.auth.mapper.UserMapper;
import com.ailearn.platform.auth.mapper.UserSessionMapper;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.auth.service.admin.TenantAdminService;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.NotFoundException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户后台管理业务服务实现类。
 */
@Service
public class TenantAdminServiceImpl implements TenantAdminService {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminServiceImpl.class);

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final UserSessionMapper userSessionMapper;
    private final SessionCacheService sessionCacheService;

    public TenantAdminServiceImpl(TenantMapper tenantMapper,
                                  UserMapper userMapper,
                                  UserSessionMapper userSessionMapper,
                                  SessionCacheService sessionCacheService) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.userSessionMapper = userSessionMapper;
        this.sessionCacheService = sessionCacheService;
    }

    /**
     * 获取当前安全上下文中的租户详细画像。
     * <p>
     * 【用途】供管理后台展示当前租户的编码、名称、状态与创建时间。
     * 主要入参：无（严格从 TenantContextHolder 读取当前租户 ID）；
     * 返回结果：TenantAdminVo 租户详情；
     * 简要流程：从上下文获取租户 ID，查询租户表，若不存在抛出 404，否则转换为 VO 返回。
     * </p>
     *
     * @return 租户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:tenant:view')")
    public TenantAdminVo getCurrentTenantDetail() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getIsdel() != 0) {
            log.warn("[查询租户失败] 租户不存在: tenantId={}", tenantId);
            throw new NotFoundException("当前租户不存在或已被删除");
        }
        return convertToVo(tenant);
    }

    /**
     * 修改当前租户的基本信息与状态。
     * <p>
     * 【用途】供管理员更新租户的企业展示名称或运营状态。
     * 主要入参：request (租户更新参数，含名称与状态)；
     * 返回结果：更新后的 TenantAdminVo 租户详情；
     * 简要流程：从上下文获取租户 ID，加载租户实体，更新对应字段并持久化落库。
     * </p>
     *
     * @param request 租户更新请求参数
     * @return 更新后的租户管理视图对象
     */
    @Override
    @PreAuthorize("hasAuthority('auth:tenant:manage')")
    @Transactional(rollbackFor = Exception.class)
    public TenantAdminVo updateCurrentTenant(TenantUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getIsdel() != 0) {
            log.warn("[更新租户失败] 租户不存在: tenantId={}", tenantId);
            throw new NotFoundException("当前租户不存在或已被删除");
        }

        String previousStatus = tenant.getStatus();
        if (request.getTenantName() != null && !request.getTenantName().trim().isEmpty()) {
            tenant.setTenantName(request.getTenantName().trim());
        }
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            tenant.setStatus(request.getStatus().trim());
        }
        tenant.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        tenant.setUpdatedAt(LocalDateTime.now());

        // 修改：租户停用必须立即撤销租户内所有活跃会话与授权快照，避免旧 Token 在会话 TTL 内继续访问。
        if ("ACTIVE".equalsIgnoreCase(previousStatus)
                && !"ACTIVE".equalsIgnoreCase(tenant.getStatus())) {
            revokeTenantSessionsAndCaches(tenantId, LocalDateTime.now());
        }

        tenantMapper.updateById(tenant);
        log.info("[租户信息更新成功] tenantId={}, updatedBy={}", tenantId, currentUsername);

        return convertToVo(tenant);
    }

    /**
     * 撤销指定租户全部用户的活跃会话并清除授权快照。
     * 主要入参为租户 ID 与撤销时间；无业务返回值；流程为先批量废弃数据库会话，再逐用户删除 Redis
     * 活跃 JTI、权限和菜单缓存，任一缓存故障直接抛出 503，保持租户停用 Fail-Closed。
     *
     * @param tenantId 租户 ID
     * @param revokedAt 撤销时间
     */
    private void revokeTenantSessionsAndCaches(UUID tenantId, LocalDateTime revokedAt) {
        userSessionMapper.revokeActiveSessionsByTenantId(tenantId, revokedAt, "TENANT_DISABLED");
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .eq(User::getTenantId, tenantId)
                .eq(User::getIsdel, 0));
        for (User user : users) {
            sessionCacheService.removeActiveSession(tenantId, user.getId());
            sessionCacheService.evictUserAuthCache(tenantId, user.getId());
        }
    }

    /**
     * 将 Tenant 实体转换为 TenantAdminVo 视图对象。
     *
     * @param tenant 租户实体
     * @return 租户视图对象
     */
    private TenantAdminVo convertToVo(Tenant tenant) {
        TenantAdminVo vo = new TenantAdminVo();
        vo.setId(tenant.getId());
        vo.setTenantCode(tenant.getTenantCode());
        vo.setTenantName(tenant.getTenantName());
        vo.setStatus(tenant.getStatus());
        vo.setCreatedBy(tenant.getCreatedBy());
        vo.setCreatedAt(tenant.getCreatedAt());
        vo.setUpdatedBy(tenant.getUpdatedBy());
        vo.setUpdatedAt(tenant.getUpdatedAt());
        return vo;
    }
}
