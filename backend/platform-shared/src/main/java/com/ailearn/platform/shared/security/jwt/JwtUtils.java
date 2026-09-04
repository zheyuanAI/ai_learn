package com.ailearn.platform.shared.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 基于 RSA 非对称加密的 JWT 令牌签发与验签工具类。
 */
public final class JwtUtils {

    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_TENANT_ID_SNAKE = "tenant_id";
    public static final String CLAIM_USERNAME = "username";
    /** 旧权限声明名称，仅保留常量以兼容编译，不再生成或解析。 */
    @Deprecated
    public static final String CLAIM_AUTHORITIES = "authorities";
    /** 旧角色声明名称，仅保留常量以兼容编译，不再生成或解析。 */
    @Deprecated
    public static final String CLAIM_ROLES = "roles";
    /** 旧权限声明名称，仅保留常量以兼容编译，不再生成或解析。 */
    @Deprecated
    public static final String CLAIM_PERMISSIONS = "permissions";

    private JwtUtils() {
    }

    /**
     * 使用 RSA 私钥签发 JWT 访问令牌。
     *
     * @param payload    令牌载荷数据
     * @param privateKey RSA 私钥
     * @param ttl        令牌有效期
     * @return 签名的 JWT 字符串
     */
    public static String generateToken(TokenPayload payload, PrivateKey privateKey, Duration ttl) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + ttl.toMillis());

        String jti = payload.getJti() != null ? payload.getJti() : UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(payload.getUserId())
                .id(jti)
                .issuedAt(now)
                .expiration(exp)
                .claim(CLAIM_TENANT_ID, payload.getTenantId())
                .claim(CLAIM_USERNAME, payload.getUsername())
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 使用 RSA 公钥解析并验证 JWT 令牌。
     *
     * @param token     JWT 字符串
     * @param publicKey RSA 公钥
     * @return Claims 载荷
     * @throws ExpiredJwtException 当令牌已过期时抛出
     * @throws JwtException        当签名无效或格式不合法时抛出
     */
    public static Claims parseAndVerify(String token, PublicKey publicKey) throws JwtException {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Claims 中提取最小身份 TokenPayload 对象，兼容 tenant_id / tenantId。
     *
     * @param claims JWT Claims 载荷
     * @return 转换后的 TokenPayload
     */
    public static TokenPayload extractPayload(Claims claims) {
        String userId = claims.getSubject();
        String jti = claims.getId();

        String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
        if (!StringUtils.hasText(tenantId)) {
            Object snakeTenantId = claims.get(CLAIM_TENANT_ID_SNAKE);
            if (snakeTenantId != null) {
                tenantId = snakeTenantId.toString();
            }
        }

        String username = claims.get(CLAIM_USERNAME, String.class);

        TokenPayload payload = new TokenPayload();
        payload.setUserId(userId);
        payload.setJti(jti);
        payload.setTenantId(tenantId);
        payload.setUsername(username);
        payload.setIssuedAt(claims.getIssuedAt());
        payload.setExpiresAt(claims.getExpiration());

        return payload;
    }
}
