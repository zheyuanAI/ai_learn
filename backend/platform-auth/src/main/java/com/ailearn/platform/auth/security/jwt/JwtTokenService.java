package com.ailearn.platform.auth.security.jwt;

import com.ailearn.platform.auth.config.JwtProperties;
import com.ailearn.platform.shared.exception.AuthException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * JWT Token 签发与解析核心服务。
 * <p>
 * 严格遵循不可变身份载荷最小化原则：
 * 载荷仅包含 sub (userId), jti, tenant_id, username, iss, aud, iat, exp；
 * 绝对不将可变的角色与功能权限点列表固化在 Token 内部。
 * </p>
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtProperties jwtProperties;
    private final RsaKeyProvider rsaKeyProvider;

    public JwtTokenService(JwtProperties jwtProperties, RsaKeyProvider rsaKeyProvider) {
        this.jwtProperties = jwtProperties;
        this.rsaKeyProvider = rsaKeyProvider;
    }

    /**
     * 签发 RSA 签名 JWT Token。
     *
     * @param userId   用户唯一 ID
     * @param tenantId 租户唯一 ID
     * @param username 用户登录账号名
     * @param jti      本次登录唯一会话标识符
     * @return 序列化后的 JWT 字符串
     */
    public String generateToken(UUID userId, UUID tenantId, String username, String jti) {
        Date now = new Date();
        long expirationMs = jwtProperties.getAccessTokenExpirationSeconds() * 1000L;
        Date expirationTime = new Date(now.getTime() + expirationMs);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .jwtID(jti)
                .claim("tenant_id", tenantId.toString())
                .claim("username", username)
                .issuer(jwtProperties.getIssuer())
                .audience(jwtProperties.getAudience())
                .issueTime(now)
                .expirationTime(expirationTime)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(jwtProperties.getRsaKeyId())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);

        try {
            signedJWT.sign(rsaKeyProvider.getSigner());
            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("[JWT签发失败] userId={}, jti={}, error={}", userId, jti, e.getMessage());
            throw new AuthException("JWT 令牌签发异常: " + e.getMessage());
        }
    }

    /**
     * 解析并验证 JWT Token（验签与过期校验）。
     *
     * @param token 客户端传入的 JWT 字符串
     * @return 校验通过后的 {@link JWTClaimsSet}
     * @throws AuthException 当签名无效、格式错误或令牌过期时抛出
     */
    public JWTClaimsSet parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException("缺失 Authorization 令牌");
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            // 校验 RSA 签名
            if (!signedJWT.verify(rsaKeyProvider.getVerifier())) {
                log.warn("[JWT验签失败] 签名不匹配");
                throw new AuthException("无效的访问令牌签名");
            }

            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            // 校验过期时间
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime != null && new Date().after(expirationTime)) {
                log.warn("[JWT已过期] exp={}, now={}", expirationTime, new Date());
                throw new AuthException("访问令牌已过期，请重新登录");
            }

            return claimsSet;
        } catch (AuthException ae) {
            throw ae;
        } catch (Exception e) {
            log.warn("[JWT解析异常] error={}", e.getMessage());
            throw new AuthException("无法解析访问令牌: " + e.getMessage());
        }
    }
}
