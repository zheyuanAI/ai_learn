package com.ailearn.platform.auth.service.admin.impl;

import com.ailearn.platform.auth.domain.dto.admin.PermissionQueryRequest;
import com.ailearn.platform.auth.domain.entity.Permission;
import com.ailearn.platform.auth.domain.entity.Role;
import com.ailearn.platform.auth.domain.vo.admin.PermissionAdminVo;
import com.ailearn.platform.auth.mapper.PermissionMapper;
import com.ailearn.platform.auth.mapper.RoleMapper;
import com.ailearn.platform.auth.service.admin.PermissionAdminService;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 权限点后台管理业务服务实现类。
 */
@Service
public class PermissionAdminServiceImpl implements PermissionAdminService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAdminServiceImpl.class);

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;

    public PermissionAdminServiceImpl(PermissionMapper permissionMapper, RoleMapper roleMapper) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 根据模块、编码与名称条件检索系统权限点字典列表。
     * <p>
     * 【用途】供管理后台权限清单页面展示与模块分组筛选。
     * 主要入参：request (module, permissionCode, permissionName)；
     * 返回结果：PermissionAdminVo 列表；
     * 简要流程：按条件过滤并按所属模块和编码升序组织返回。
     * </p>
     *
     * @param request 权限点查询筛选参数
     * @return 权限点视图对象列表
     */
    @Override
    public List<PermissionAdminVo> listPermissions(PermissionQueryRequest request) {
        String module = request != null ? request.getModule() : null;
        String permissionCode = request != null ? request.getPermissionCode() : null;
        String permissionName = request != null ? request.getPermissionName() : null;

        List<Permission> permissions = permissionMapper.selectPermissionsByCondition(module, permissionCode, permissionName);
        List<PermissionAdminVo> voList = new ArrayList<>();
        for (Permission p : permissions) {
            voList.add(convertToVo(p));
        }
        return voList;
    }

    /**
     * 查询指定角色拥有的功能权限点实体列表。
     * <p>
     * 【用途】供管理后台查看指定角色的权限明细画像。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：分配给该角色的 PermissionAdminVo 列表；
     * 简要流程：核验租户隔离，多表联查已授权的未删除权限点清单。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 权限点视图对象列表
     */
    @Override
    public List<PermissionAdminVo> getRolePermissions(UUID roleId) {
        verifyRoleBelongsToCurrentTenant(roleId);
        List<Permission> permissions = permissionMapper.findPermissionsByRoleId(roleId);
        List<PermissionAdminVo> voList = new ArrayList<>();
        for (Permission p : permissions) {
            voList.add(convertToVo(p));
        }
        return voList;
    }

    /**
     * 查询指定角色拥有的功能权限点 ID 列表。
     * <p>
     * 【用途】供角色编辑授权面板快速回显已勾选的权限点 ID 集合。
     * 主要入参：roleId (目标角色ID)；
     * 返回结果：权限点 UUID 列表；
     * 简要流程：核验租户隔离，查询 auth_role_permission 中的 permission_id 集合。
     * </p>
     *
     * @param roleId 目标角色 ID
     * @return 权限点 ID 列表
     */
    @Override
    public List<UUID> getRolePermissionIds(UUID roleId) {
        verifyRoleBelongsToCurrentTenant(roleId);
        return permissionMapper.findPermissionIdsByRoleId(roleId);
    }

    /**
     * 核验角色属于当前租户且未删除。
     */
    private void verifyRoleBelongsToCurrentTenant(UUID roleId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Role role = roleMapper.selectById(roleId);
        if (role == null || !tenantId.equals(role.getTenantId()) || role.getIsdel() != 0) {
            log.warn("[读取角色权限失败] 角色不存在或跨租户: roleId={}, tenantId={}", roleId, tenantId);
            throw new NotFoundException("角色不存在或不属于当前租户");
        }
    }

    /**
     * 实体转 VO 转换方法。
     */
    private PermissionAdminVo convertToVo(Permission p) {
        PermissionAdminVo vo = new PermissionAdminVo();
        vo.setId(p.getId());
        vo.setPermissionCode(p.getPermissionCode());
        vo.setPermissionName(p.getPermissionName());
        vo.setModule(p.getModule());
        vo.setDescription(p.getDescription());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
