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
 * 租户实体（auth_tenant）。
 * <p>
 * 定义多租户隔离上下文中的租户基础信息与状态。
 * </p>
 */
@TableName("auth_tenant")
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 租户业务编码（全局唯一，例如 DEFAULT, FACTORY_01）
     */
    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 租户名称
     */
    @TableField("tenant_name")
    private String tenantName;

    /**
     * 租户状态（ACTIVE: 正常, DISABLED: 禁用）
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

    public Tenant() {
        this.status = "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Tenant(UUID id, String tenantCode, String tenantName, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantCode = tenantCode;
        this.tenantName = tenantName;
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

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
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
        if (!(o instanceof Tenant tenant)) return false;
        return Objects.equals(id, tenant.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
