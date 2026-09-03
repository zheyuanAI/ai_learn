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
 * 用户与角色关联实体（auth_user_role）。
 * <p>
 * 绑定特定租户下的用户与其分配的角色关系。
 * </p>
 */
@TableName("auth_user_role")
public class UserRole implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联记录唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 所属租户 ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 角色 ID
     */
    @TableField("role_id")
    private UUID roleId;

    /**
     * 创建时间
     */
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 逻辑删除标识（0: 未删除, 1: 已删除）
     */
    @TableLogic
    @TableField("isdel")
    private Integer isdel;

    public UserRole() {
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
    }

    public UserRole(UUID id, UUID tenantId, UUID userId, UUID roleId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.roleId = roleId;
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
        if (!(o instanceof UserRole userRole)) return false;
        return Objects.equals(id, userRole.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
