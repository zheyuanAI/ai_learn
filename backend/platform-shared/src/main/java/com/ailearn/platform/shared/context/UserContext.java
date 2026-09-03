package com.ailearn.platform.shared.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 平台统一用户身份与权限安全上下文。
 * <p>
 * 用于在网关解析或下游 Filter 拦截后，存储受信任的用户身份、租户 ID、会话 ID 及权限集合。
 * </p>
 */
public class UserContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识 (userId)
     */
    private String userId;

    /**
     * 租户唯一标识 (tenantId)
     */
    private String tenantId;

    /**
     * 登录账号名 (username)
     */
    private String username;

    /**
     * 登录会话标识 (jti / sessionId)
     */
    private String sessionId;

    /**
     * 请求链路追踪 ID (requestId)
     */
    private String requestId;

    /**
     * 用户拥有的所有权限与角色标识集合（用于 Spring Security 授权匹配）
     */
    private Set<String> authorities = new HashSet<>();

    /**
     * 业务角色集合
     */
    private Set<String> roles = new HashSet<>();

    /**
     * 细粒度功能权限集合
     */
    private Set<String> permissions = new HashSet<>();

    public UserContext() {
    }

    public UserContext(String userId, String tenantId, String username, String sessionId, String requestId, Set<String> authorities) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.setAuthorities(authorities);
    }

    /**
     * 检查当前用户是否具有指定权限或角色。
     *
     * @param authority 权限或角色字符串
     * @return 若持有返回 true，否则返回 false
     */
    public boolean hasAuthority(String authority) {
        return authority != null && authorities.contains(authority);
    }

    /**
     * 检查当前用户是否具有指定角色。
     *
     * @param role 角色标识（支持带或不带 ROLE_ 前缀）
     * @return 若持有该角色返回 true
     */
    public boolean hasRole(String role) {
        if (role == null) {
            return false;
        }
        if (roles.contains(role)) {
            return true;
        }
        String withPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        String withoutPrefix = role.startsWith("ROLE_") ? role.substring(5) : role;
        return authorities.contains(withPrefix) || authorities.contains(withoutPrefix);
    }

    /**
     * 检查当前用户是否具有指定权限点。
     *
     * @param permission 权限标识字符串
     * @return 若持有返回 true
     */
    public boolean hasPermission(String permission) {
        return permission != null && (permissions.contains(permission) || authorities.contains(permission));
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Set<String> getAuthorities() {
        return authorities != null ? authorities : Collections.emptySet();
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities != null ? new HashSet<>(authorities) : new HashSet<>();
        // 自动解析角色与权限
        this.roles = new HashSet<>();
        this.permissions = new HashSet<>();
        for (String auth : this.authorities) {
            if (auth.startsWith("ROLE_")) {
                this.roles.add(auth.substring(5));
            } else {
                this.permissions.add(auth);
            }
        }
    }

    public Set<String> getRoles() {
        return roles != null ? roles : Collections.emptySet();
    }

    public Set<String> getPermissions() {
        return permissions != null ? permissions : Collections.emptySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserContext that)) return false;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tenantId, username, sessionId);
    }

    @Override
    public String toString() {
        return "UserContext{" +
                "userId='" + userId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", username='" + username + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", authorities=" + authorities +
                '}';
    }
}
