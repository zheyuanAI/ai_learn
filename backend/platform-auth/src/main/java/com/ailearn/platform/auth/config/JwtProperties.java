package com.ailearn.platform.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 认证与 RSA 签名配置属性类。
 */
@Component
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {

    /**
     * JWT 签发者标识 (iss)
     */
    private String issuer = "ai-learn-platform";

    /**
     * JWT 受众标识 (aud)
     */
    private String audience = "ai-learn-platform";

    /**
     * Access Token 有效时间（秒），默认 7200 秒（2 小时）
     */
    private long accessTokenExpirationSeconds = 7200L;

    /**
     * RSA 密钥唯一标识 (kid)
     */
    private String rsaKeyId = "ai-learn-auth-key-1";

    /**
     * RSA 私钥 PEM（非生产环境默认使用稳定开发私钥）
     */
    private String privateKey;

    /**
     * RSA 公钥 PEM（非生产环境默认使用稳定开发公钥）
     */
    private String publicKey;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public void setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public String getRsaKeyId() {
        return rsaKeyId;
    }

    public void setRsaKeyId(String rsaKeyId) {
        this.rsaKeyId = rsaKeyId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
