package com.ailearn.platform.shared.security.jwt;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * JWT Token 载荷传输实体。
 */
public class TokenPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识（对应 JWT sub）
     */
    private String userId;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 登录账号名
     */
    private String username;

    /**
     * 会话唯一标识 (JTI)
     */
    private String jti;

    /**
     * 角色与权限集合（对应 authorities）
     */
    private Set<String> authorities = new HashSet<>();

    public TokenPayload() {
    }

    public TokenPayload(String userId, String tenantId, String username, String jti, Collection<String> authorities) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.jti = jti;
        if (authorities != null) {
            this.authorities = new HashSet<>(authorities);
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities != null ? new HashSet<>(authorities) : new HashSet<>();
    }
}
