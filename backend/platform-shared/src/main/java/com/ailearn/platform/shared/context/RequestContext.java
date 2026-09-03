package com.ailearn.platform.shared.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 平台请求级上下文实体对象。
 * <p>
 * 保存当前 HTTP 请求关联的安全与多租户凭据，包括租户 ID、用户 ID、账号名、会话 JTI、角色集、权限点集、请求追踪 ID 等。
 * </p>
 */
public class RequestContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前租户 ID
     */
    private UUID tenantId;

    /**
     * 当前用户 ID
     */
    private UUID userId;

    /**
     * 当前登录账号名
     */
    private String username;

    /**
     * JWT 会话唯一标识符（JTI）
     */
    private String jti;

    /**
     * 当前用户分配的业务角色集合
     */
    private Set<String> roles = new HashSet<>();

    /**
     * 当前用户分配的功能权限点集合
     */
    private Set<String> permissions = new HashSet<>();

    /**
     * 当前链路请求追踪 ID (X-Request-Id)
     */
    private String requestId;

    /**
     * 客户端来源 IP
     */
    private String clientIp;

    /**
     * 请求生命周期内的自定义扩展属性表
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 默认构造函数。
     */
    public RequestContext() {
    }

    /**
     * 检查当前上下文是否包含指定角色。
     *
     * @param role 角色标识（例如 "TENANT_ADMIN", "SALES" 等）
     * @return 若持有该角色返回 true，否则返回 false
     */
    public boolean hasRole(String role) {
        if (role == null || roles == null) {
            return false;
        }
        return roles.contains(role);
    }

    /**
     * 检查当前上下文是否包含指定权限点。
     *
     * @param permission 权限标识字符串（例如 "purchase:order:approve"）
     * @return 若持有该权限返回 true，否则返回 false
     */
    public boolean hasPermission(String permission) {
        if (permission == null || permissions == null) {
            return false;
        }
        return permissions.contains(permission);
    }

    /**
     * 设置自定义扩展属性。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, value);
    }

    /**
     * 获取自定义扩展属性。
     *
     * @param key 属性键
     * @param <T> 目标类型
     * @return 属性值或 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return attributes != null ? (T) attributes.get(key) : null;
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

    public Set<String> getRoles() {
        return roles != null ? roles : Collections.emptySet();
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
    }

    public Set<String> getPermissions() {
        return permissions != null ? permissions : Collections.emptySet();
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = permissions != null ? new HashSet<>(permissions) : new HashSet<>();
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes != null ? attributes : new HashMap<>();
    }
}
