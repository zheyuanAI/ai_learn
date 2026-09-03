package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * JWT 密钥管理与 Token 签发/解析工具组件。
 * <p>
 * 基于 JJWT 0.12.x 与 RSA (RS256) 非对称加密算法：
 * <ul>
 *   <li>Auth 认证服务持有私钥用于签名签发 Token</li>
 *   <li>Gateway 与各个微服务持有公钥用于离线验签与解析 Token 载荷</li>
 * </ul>
 * 载荷遵循平台规范：包含 sub (userId), jti (会话唯一ID), tenant_id, username, roles, permissions 等。
 * </p>
 */
public class JwtKeyProvider {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    /**
     * 基于 RSA 密钥对构造提供者（适用于 Auth 认证中心）。
     *
     * @param keyPair RSA 密钥对
     */
    public JwtKeyProvider(KeyPair keyPair) {
        if (keyPair == null) {
            throw new IllegalArgumentException("KeyPair 不能为空");
        }
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
    }

    /**
     * 基于单独公钥与私钥构造提供者。
     *
     * @param publicKey  RSA 公钥（验签必填）
     * @param privateKey RSA 私钥（签发可选，仅网关验签时可为 null）
     */
    public JwtKeyProvider(PublicKey publicKey, PrivateKey privateKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("RSA 公钥不能为空");
        }
        this.publicKey = (RSAPublicKey) publicKey;
        this.privateKey = (RSAPrivateKey) privateKey;
    }

    /**
     * 基于 PEM 字符串初始化提供者。
     *
     * @param publicKeyPem  公钥 PEM 文本
     * @param privateKeyPem 私钥 PEM 文本（非认证服务可为 null）
     */
    public JwtKeyProvider(String publicKeyPem, String privateKeyPem) {
        this.publicKey = StringUtils.hasText(publicKeyPem) ? RsaKeyUtils.parsePublicKey(publicKeyPem) : null;
        this.privateKey = StringUtils.hasText(privateKeyPem) ? RsaKeyUtils.parsePrivateKey(privateKeyPem) : null;
        if (this.publicKey == null && this.privateKey == null) {
            throw new IllegalArgumentException("公钥与私钥不能同时为空");
        }
    }

    /**
     * 签发标准业务 JWT Access Token。
     *
     * @param tenantId    租户 UUID
     * @param userId      用户 UUID (存入 subject)
     * @param username    用户名
     * @param jti         会话唯一 ID
     * @param roles       用户角色列表
     * @param permissions 用户权限点列表
     * @param ttl         Token 有效期
     * @return 签名后的 JWT Token 字符串
     */
    public String createToken(UUID tenantId,
                              UUID userId,
                              String username,
                              String jti,
                              Collection<String> roles,
                              Collection<String> permissions,
                              Duration ttl) {
        if (privateKey == null) {
            throw new IllegalStateException("当前未配置 RSA 私钥，无法签发 JWT Token");
        }

        Instant now = Instant.now();
        Instant exp = now.plus(ttl);

        var builder = Jwts.builder()
                .subject(userId != null ? userId.toString() : null)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_USERNAME, username)
                .signWith(privateKey, Jwts.SIG.RS256);

        if (tenantId != null) {
            builder.claim(CLAIM_TENANT_ID, tenantId.toString());
        }
        if (roles != null && !roles.isEmpty()) {
            builder.claim(CLAIM_ROLES, roles);
        }
        if (permissions != null && !permissions.isEmpty()) {
            builder.claim(CLAIM_PERMISSIONS, permissions);
        }

        return builder.compact();
    }

    /**
     * 校验并解析 JWT Token 载荷（Claims）。
     *
     * @param token JWT 字符串
     * @return {@link Claims} 载荷对象
     * @throws AuthException 当 Token 过期、签名非法或格式损坏时抛出
     */
    public Claims parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new AuthException("Token 不能为空");
        }
        if (publicKey == null) {
            throw new IllegalStateException("当前未配置 RSA 公钥，无法验证 JWT Token");
        }

        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException("登录已过期，请重新登录", e);
        } catch (JwtException e) {
            throw new AuthException("Token 签名非法或格式错误: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AuthException("Token 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JWT Token 中直接还原 {@link RequestContext} 上下文对象。
     *
     * @param token JWT 字符串
     * @return 请求上下文实体
     */
    @SuppressWarnings("unchecked")
    public RequestContext parseRequestContext(String token) {
        Claims claims = parseToken(token);
        RequestContext context = new RequestContext();

        // sub -> userId
        String sub = claims.getSubject();
        if (StringUtils.hasText(sub)) {
            try {
                context.setUserId(UUID.fromString(sub));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // jti
        context.setJti(claims.getId());

        // tenant_id
        Object tenantIdObj = claims.get(CLAIM_TENANT_ID);
        if (tenantIdObj != null) {
            try {
                context.setTenantId(UUID.fromString(tenantIdObj.toString()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // username
        Object usernameObj = claims.get(CLAIM_USERNAME);
        if (usernameObj != null) {
            context.setUsername(usernameObj.toString());
        }

        // roles
        Object rolesObj = claims.get(CLAIM_ROLES);
        if (rolesObj instanceof List<?> list) {
            Set<String> roleSet = new HashSet<>();
            for (Object r : list) {
                if (r != null) {
                    roleSet.add(r.toString());
                }
            }
            context.setRoles(roleSet);
        }

        // permissions
        Object permsObj = claims.get(CLAIM_PERMISSIONS);
        if (permsObj instanceof List<?> list) {
            Set<String> permSet = new HashSet<>();
            for (Object p : list) {
                if (p != null) {
                    permSet.add(p.toString());
                }
            }
            context.setPermissions(permSet);
        }

        return context;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }
}
