package com.ailearn.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户会话事实记录实体（auth_session）。
 * <p>
 * 持久化记录用户登录签发的 JTI、会话状态（ACTIVE / REVOKED / EXPIRED）、客户端环境及起止时间。
 * 作为单账号单有效会话控制（后登顶前）的底层事实依据。
 * </p>
 */
@TableName("auth_session")
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话唯一标识 ID
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
     * JWT 唯一会话标识符（JTI）
     */
    @TableField("jti")
    private String jti;

    /**
     * 客户端登录 IP
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 客户端 User-Agent
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 登录时间
     */
    @TableField("login_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime loginAt;

    /**
     * 预计会话过期时间
     */
    @TableField("expires_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime expiresAt;

    /**
     * 实际被撤销/顶替时间
     */
    @TableField("revoked_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime revokedAt;

    /**
     * 撤销原因（如 LOGOUT, REPLACED_BY_NEW_LOGIN, ADMIN_KICK）
     */
    @TableField("revoked_reason")
    private String revokedReason;

    /**
     * 会话状态（ACTIVE: 活跃有效, REVOKED: 已被顶替或注销撤销, EXPIRED: 自然过期）
     */
    @TableField("status")
    private String status;

    public UserSession() {
        this.status = "ACTIVE";
        this.loginAt = LocalDateTime.now();
    }

    public UserSession(UUID id, UUID tenantId, UUID userId, String jti, String status, String ipAddress, String userAgent, LocalDateTime loginAt, LocalDateTime expiresAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.jti = jti;
        this.status = status != null ? status : "ACTIVE";
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.loginAt = loginAt != null ? loginAt : LocalDateTime.now();
        this.expiresAt = expiresAt;
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

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(LocalDateTime loginAt) {
        this.loginAt = loginAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
