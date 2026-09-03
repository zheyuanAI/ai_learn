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
 * 角色与权限关联实体（auth_role_permission）。
 * <p>
 * 定义角色所拥有的功能权限点授权清单。
 * </p>
 */
@TableName("auth_role_permission")
public class RolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联记录唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 角色 ID
     */
    @TableField("role_id")
    private UUID roleId;

    /**
     * 权限点 ID
     */
    @TableField("permission_id")
    private UUID permissionId;

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

    public RolePermission() {
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
    }

    public RolePermission(UUID id, UUID roleId, UUID permissionId) {
        this.id = id != null ? id : UUID.randomUUID();
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
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
        if (!(o instanceof RolePermission that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
