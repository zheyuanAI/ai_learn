package com.ailearn.platform.shared.security.jwt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
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
     * JWT 签发时间，对应标准 iat 声明。
     */
    private Date issuedAt;

    /**
     * JWT 过期时间，对应标准 exp 声明。
     */
    private Date expiresAt;

    /**
     * 标记旧版带权限集合构造器，用于让 JwtUtils 在兼容编译调用方时不再写入权限声明。
     */
    private transient boolean suppressLegacyAuthorityClaim;

    /**
     * 创建空的最小身份载荷，供 JWT 解析结果填充。
     */
    public TokenPayload() {
    }

    /**
     * 创建最小身份载荷。
     *
     * @param userId 用户唯一标识，对应 JWT sub
     * @param tenantId 租户唯一标识
     * @param username 登录账号名
     * @param jti 会话唯一标识
     * @param issuedAt 签发时间，对应 JWT iat
     * @param expiresAt 过期时间，对应 JWT exp
     */
    public TokenPayload(
            String userId,
            String tenantId,
            String username,
            String jti,
            Date issuedAt,
            Date expiresAt) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.jti = jti;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 兼容旧版带权限集合的构造签名，但明确丢弃权限集合，避免权限进入 JWT 载荷。
     *
     * @param userId 用户唯一标识，对应 JWT sub
     * @param tenantId 租户唯一标识
     * @param username 登录账号名
     * @param jti 会话唯一标识
     * @param ignoredAuthorities 已废弃的权限参数，仅为源码兼容保留且不会存储
     */
    @Deprecated
    public TokenPayload(
            String userId,
            String tenantId,
            String username,
            String jti,
            Collection<String> ignoredAuthorities) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.jti = jti;
        this.suppressLegacyAuthorityClaim = true;
    }

    /**
     * 获取用户唯一标识。
     *
     * @return JWT sub 对应的用户标识
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置用户唯一标识。
     *
     * @param userId 用户唯一标识
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 获取租户唯一标识。
     *
     * @return 租户标识
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置租户唯一标识。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 获取登录账号名。
     *
     * @return 登录账号名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置登录账号名。
     *
     * @param username 登录账号名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取会话唯一标识。
     *
     * @return JWT jti
     */
    public String getJti() {
        return jti;
    }

    /**
     * 设置会话唯一标识。
     *
     * @param jti JWT jti
     */
    public void setJti(String jti) {
        this.jti = jti;
    }

    /**
     * 获取签发时间。
     *
     * @return JWT iat
     */
    public Date getIssuedAt() {
        return issuedAt;
    }

    /**
     * 设置签发时间。
     *
     * @param issuedAt JWT iat
     */
    public void setIssuedAt(Date issuedAt) {
        this.issuedAt = issuedAt;
    }

    /**
     * 获取过期时间。
     *
     * @return JWT exp
     */
    public Date getExpiresAt() {
        return expiresAt;
    }

    /**
     * 设置过期时间。
     *
     * @param expiresAt JWT exp
     */
    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * 兼容 JwtUtils 的旧权限读取调用，但永不保存或返回权限集合。
     * 旧解析路径仍可向结果对象执行 add，而这些值会被立即丢弃。
     *
     * @return 旧带权限构造器返回 null，其余场景返回临时空集合
     */
    @JsonIgnore
    @Deprecated
    public Set<String> getAuthorities() {
        return suppressLegacyAuthorityClaim ? null : new HashSet<>();
    }

    /**
     * 兼容旧版 JavaBean 调用，但丢弃角色与权限，不让其进入 TokenPayload。
     *
     * @param ignoredAuthorities 已废弃的权限集合
     */
    @JsonIgnore
    @Deprecated
    public void setAuthorities(Set<String> ignoredAuthorities) {
        // 刻意忽略旧权限字段，JWT 只允许承载最小身份信息。
    }
}
