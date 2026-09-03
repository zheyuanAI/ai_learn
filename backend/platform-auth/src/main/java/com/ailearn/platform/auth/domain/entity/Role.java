package com.ailearn.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 角色实体（auth_role）。
 * <p>
 * 定义系统中的业务角色，关联功能权限点与菜单。
 * </p>
 */
@TableName("auth_role")
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 所属租户 ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 角色业务标识编码（例如 TENANT_ADMIN, SALES, PURCHASING, WAREHOUSE, MES_INSPECTOR, IOT_ENGINEER）
     */
    @TableField("role_code")
    private String roleCode;

    /**
     * 角色展示名称（例如 租户管理员, 销售人员, 采购人员, 仓库人员, 生产质检人员, IoT人员）
     */
    @TableField("role_name")
    private String roleName;

    /**
     * 角色功能描述
     */
    @TableField("description")
    private String description;

    /**
     * 状态（ACTIVE: 正常, DISABLED: 禁用）
     */
    @TableField("status")
    private String status;

    /**
     * 创建人
     */
    @TableField("created_by")
    private String createdBy;

    /**
     * 创建时间
     */
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 更新人
     */
    @TableField("updated_by")
    private String updatedBy;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标识（0: 未删除, 1: 已删除）
     */
    @TableLogic
    @TableField("isdel")
    private Integer isdel;

    public Role() {
        this.status = "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Role(UUID id, UUID tenantId, String roleCode, String roleName, String description, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.status = status != null ? status : "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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

    public Integer getIsdel() {
        return isdel;
    }

    public void setIsdel(Integer isdel) {
        this.isdel = isdel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role role)) return false;
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
