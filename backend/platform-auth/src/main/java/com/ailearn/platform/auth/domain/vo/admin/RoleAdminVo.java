package com.ailearn.platform.auth.domain.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 角色后台管理视图对象 VO。
 * <p>
 * 封装角色的元数据、状态、已分配权限点与菜单 ID 集合，以及关联用户数统计。
 * </p>
 */
@Schema(description = "角色后台管理详情对象")
public class RoleAdminVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色唯一标识 ID
     */
    @Schema(description = "角色唯一标识 ID", example = "20000000-0000-0000-0000-000000000001")
    private UUID id;

    /**
     * 所属租户 ID
     */
    @Schema(description = "所属租户 ID", example = "a0000000-0000-0000-0000-000000000001")
    private UUID tenantId;

    /**
     * 角色业务标识编码
     */
    @Schema(description = "角色业务标识编码", example = "TENANT_ADMIN")
    private String roleCode;

    /**
     * 角色展示名称
     */
    @Schema(description = "角色展示名称", example = "租户管理员")
    private String roleName;

    /**
     * 角色描述说明
     */
    @Schema(description = "角色描述说明", example = "负责租户内账号、角色、权限与菜单配置")
    private String description;

    /**
     * 状态（ACTIVE: 正常, DISABLED: 禁用）
     */
    @Schema(description = "角色状态 (ACTIVE / DISABLED)", example = "ACTIVE")
    private String status;

    /**
     * 已分配此角色的有效用户总数
     */
    @Schema(description = "关联用户数统计", example = "3")
    private Integer userCount;

    /**
     * 已分配的功能权限点总数
     */
    @Schema(description = "关联权限点数统计", example = "15")
    private Integer permissionCount;

    /**
     * 已分配的功能权限点 ID 列表
     */
    @Schema(description = "已分配的权限点 ID 列表")
    private List<UUID> permissionIds;

    /**
     * 已分配的菜单 ID 列表
     */
    @Schema(description = "已分配的菜单 ID 列表")
    private List<UUID> menuIds;

    /**
     * 创建人
     */
    @Schema(description = "创建人", example = "system")
    private String createdBy;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-08-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 最后更新人
     */
    @Schema(description = "最后更新人", example = "admin.zhang")
    private String updatedBy;

    /**
     * 最后更新时间
     */
    @Schema(description = "最后更新时间", example = "2026-08-02 15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    public RoleAdminVo() {
        this.userCount = 0;
        this.permissionCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getUserCount() {
        return userCount;
    }

    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }

    public Integer getPermissionCount() {
        return permissionCount;
    }

    public void setPermissionCount(Integer permissionCount) {
        this.permissionCount = permissionCount;
    }

    public List<UUID> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }

    public List<UUID> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<UUID> menuIds) {
        this.menuIds = menuIds;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
