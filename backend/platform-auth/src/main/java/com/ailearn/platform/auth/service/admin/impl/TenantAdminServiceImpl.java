package com.ailearn.platform.auth.service.admin.impl;

import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Tenant;
import com.ailearn.platform.auth.domain.vo.admin.TenantAdminVo;
import com.ailearn.platform.auth.mapper.TenantMapper;
import com.ailearn.platform.auth.service.admin.TenantAdminService;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户后台管理业务服务实现类。
 */
@Service
public class TenantAdminServiceImpl implements TenantAdminService {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminServiceImpl.class);

    private final TenantMapper tenantMapper;

    public TenantAdminServiceImpl(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
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
    @Transactional(rollbackFor = Exception.class)
    public TenantAdminVo updateCurrentTenant(TenantUpdateRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        String currentUsername = UserContextHolder.getUsername();

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getIsdel() != 0) {
            log.warn("[更新租户失败] 租户不存在: tenantId={}", tenantId);
            throw new NotFoundException("当前租户不存在或已被删除");
        }

        if (request.getTenantName() != null && !request.getTenantName().trim().isEmpty()) {
            tenant.setTenantName(request.getTenantName().trim());
        }
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            tenant.setStatus(request.getStatus().trim());
        }
        tenant.setUpdatedBy(currentUsername != null ? currentUsername : "system");
        tenant.setUpdatedAt(LocalDateTime.now());

        tenantMapper.updateById(tenant);
        log.info("[租户信息更新成功] tenantId={}, updatedBy={}", tenantId, currentUsername);

        return convertToVo(tenant);
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
