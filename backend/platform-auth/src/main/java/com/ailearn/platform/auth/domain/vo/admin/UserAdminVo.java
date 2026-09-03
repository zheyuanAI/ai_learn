package com.ailearn.platform.auth.domain.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 用户后台管理视图对象 VO。
 * <p>
 * 封装用户的基本画像、工号、状态、所属租户及已分配的角色列表（严格安全脱敏，不含密码字段）。
 * </p>
 */
@Schema(description = "用户后台管理详情对象")
public class UserAdminVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识 ID
     */
    @Schema(description = "用户唯一标识 ID", example = "30000000-0000-0000-0000-000000000001")
    private UUID id;

    /**
     * 所属租户 ID
     */
    @Schema(description = "所属租户 ID", example = "a0000000-0000-0000-0000-000000000001")
    private UUID tenantId;

    /**
     * 员工工号/编号
     */
    @Schema(description = "员工工号", example = "EMP001")
    private String userNo;

    /**
     * 登录账号名
     */
    @Schema(description = "登录账号名", example = "admin.zhang")
    private String username;

    /**
     * 用户真实姓名
     */
    @Schema(description = "用户真实姓名", example = "张管理")
    private String realName;

    /**
     * 电子邮箱
     */
    @Schema(description = "电子邮箱", example = "admin@example.com")
    private String email;

    /**
     * 联系手机号
     */
    @Schema(description = "联系手机号", example = "13800000001")
    private String phone;

    /**
     * 账号状态（ACTIVE: 正常, DISABLED: 禁用, LOCKED: 锁定）
     */
    @Schema(description = "账号状态 (ACTIVE / DISABLED / LOCKED)", example = "ACTIVE")
    private String status;

    /**
     * 已分配的角色列表
     */
    @Schema(description = "已分配的角色列表")
    private List<UserRoleItemVo> roles;

    /**
     * 已分配的角色 ID 列表
     */
    @Schema(description = "已分配的角色 ID 列表")
    private List<UUID> roleIds;

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

    public UserAdminVo() {
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

    public String getUserNo() {
        return userNo;
    }

    public void setUserNo(String userNo) {
        this.userNo = userNo;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<UserRoleItemVo> getRoles() {
        return roles;
    }

    public void setRoles(List<UserRoleItemVo> roles) {
        this.roles = roles;
    }

    public List<UUID> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<UUID> roleIds) {
        this.roleIds = roleIds;
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

    /**
     * 简易角色条目内部 VO。
     */
    @Schema(description = "用户关联的角色概要信息")
    public static class UserRoleItemVo implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "角色 ID", example = "20000000-0000-0000-0000-000000000001")
        private UUID roleId;

        @Schema(description = "角色编码", example = "TENANT_ADMIN")
        private String roleCode;

        @Schema(description = "角色名称", example = "租户管理员")
        private String roleName;

        public UserRoleItemVo() {
        }

        public UserRoleItemVo(UUID roleId, String roleCode, String roleName) {
            this.roleId = roleId;
            this.roleCode = roleCode;
            this.roleName = roleName;
        }

        public UUID getRoleId() {
            return roleId;
        }

        public void setRoleId(UUID roleId) {
            this.roleId = roleId;
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
    }
}
