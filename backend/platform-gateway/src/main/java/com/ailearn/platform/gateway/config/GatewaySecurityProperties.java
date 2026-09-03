package com.ailearn.platform.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关安全与认证相关配置属性。
 */
@Component
@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    /**
     * 用于校验 JWT 签名的 RSA 公钥（PEM 格式文本或 Base64 字符串）
     */
    private String publicKey;

    /**
     * Auth 服务 JWKS 公钥地址；未显式配置公钥时由网关按需拉取。
     */
    private String jwksUrl = "http://localhost:10002/api/auth/jwks";

    /**
     * 是否开启 Redis 会话状态校验（支持一账号一会话踢出与状态检查）
     */
    private boolean sessionCheckEnabled = true;

    /**
     * Redis 会话 Key 前缀，默认 "auth:session:"
     */
    private String sessionKeyPrefix = "auth:session:";

    /**
     * 认证放行白名单路径列表
     */
    private List<String> whitelist = new ArrayList<>();

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public void setJwksUrl(String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public boolean isSessionCheckEnabled() {
        return sessionCheckEnabled;
    }

    public void setSessionCheckEnabled(boolean sessionCheckEnabled) {
        this.sessionCheckEnabled = sessionCheckEnabled;
    }

    public String getSessionKeyPrefix() {
        return sessionKeyPrefix;
    }

    public void setSessionKeyPrefix(String sessionKeyPrefix) {
        this.sessionKeyPrefix = sessionKeyPrefix;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
