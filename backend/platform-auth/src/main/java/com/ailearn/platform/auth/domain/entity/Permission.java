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
 * 功能权限点实体（auth_permission）。
 * <p>
 * 定义系统中的功能权限标识（例如 sales.order.create, inventory.receipt.confirm 等）。
 * </p>
 */
@TableName("auth_permission")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限点唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 权限点唯一字符串标识（例如 sales.order.create, inventory.receipt.confirm）
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 权限点名称（例如 创建销售订单, 采购外观验收与实收）
     */
    @TableField("permission_name")
    private String permissionName;

    /**
     * 所属业务模块（如 purchasing, sales, inventory, mes, iot, auth）
     */
    @TableField("module")
    private String module;

    /**
     * 权限点说明描述
     */
    @TableField("description")
    private String description;

    /**
     * 创建时间
     */
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

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

    public Permission() {
        this.module = "auth";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Permission(UUID id, String permissionCode, String permissionName, String module, String description) {
        this.id = id != null ? id : UUID.randomUUID();
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
        this.module = module != null ? module : "auth";
        this.description = description;
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

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
        if (!(o instanceof Permission that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
