package com.ailearn.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户实体（auth_user）。
 * <p>
 * 存储属于特定租户的操作用户账号基础信息、密码哈希与账号状态。
 * </p>
 */
@TableName("auth_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 所属租户 ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 用户工号/编号
     */
    @TableField("user_no")
    private String userNo;

    /**
     * 登录账号名（同租户内唯一）
     */
    @TableField("username")
    private String username;

    /**
     * 密码哈希值（BCrypt 加密密文，序列化时忽略）
     */
    @TableField("password_hash")
    @JsonIgnore
    private String passwordHash;

    /**
     * 用户真实姓名/展示姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 电子邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 联系手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 账号状态（ACTIVE: 正常, DISABLED: 禁用, LOCKED: 锁定）
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

    public User() {
        this.status = "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public User(UUID id, UUID tenantId, String userNo, String username, String passwordHash, String realName, String email, String phone, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.userNo = userNo != null ? userNo : username;
        this.username = username;
        this.passwordHash = passwordHash;
        this.realName = realName;
        this.email = email;
        this.phone = phone;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
