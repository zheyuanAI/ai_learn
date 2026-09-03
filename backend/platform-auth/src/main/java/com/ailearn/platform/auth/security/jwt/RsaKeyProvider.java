package com.ailearn.platform.auth.security.jwt;

import com.ailearn.platform.auth.config.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RSA 密钥对生命周期与 JWKS 提供者。
 * <p>
 * 初始化并管理 2048 位 RSA 非对称加密密钥对，负责创建 JWT 签名器、验签器以及输出标准 JWKS (JSON Web Key Set)。
 * </p>
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final JwtProperties jwtProperties;
    private RSAKey rsaJwk;
    private JWSSigner signer;
    private JWSVerifier verifier;

    public RsaKeyProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 容器初始化时加载稳定的 RSA 密钥对（优先读取配置，默认使用 DevRsaKeyDefaults 稳定开发密钥）。
     */
    @PostConstruct
    public void init() {
        try {
            String privateKeyPem = org.springframework.util.StringUtils.hasText(jwtProperties.getPrivateKey())
                    ? jwtProperties.getPrivateKey()
                    : com.ailearn.platform.shared.security.DevRsaKeyDefaults.DEV_PRIVATE_KEY_PEM;

            String publicKeyPem = org.springframework.util.StringUtils.hasText(jwtProperties.getPublicKey())
                    ? jwtProperties.getPublicKey()
                    : com.ailearn.platform.shared.security.DevRsaKeyDefaults.DEV_PUBLIC_KEY_PEM;

            String kid = org.springframework.util.StringUtils.hasText(jwtProperties.getRsaKeyId())
                    ? jwtProperties.getRsaKeyId()
                    : com.ailearn.platform.shared.security.DevRsaKeyDefaults.DEV_KEY_ID;

            RSAPrivateKey privateKey = com.ailearn.platform.shared.security.RsaKeyUtils.parsePrivateKey(privateKeyPem);
            RSAPublicKey publicKey = com.ailearn.platform.shared.security.RsaKeyUtils.parsePublicKey(publicKeyPem);

            this.rsaJwk = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyID(kid)
                    .build();

            this.signer = new RSASSASigner(privateKey);
            this.verifier = new RSASSAVerifier(publicKey);

            log.info("[RSA密钥初始化完成] kid={}, algorithm=RS256, keyLength=2048 (稳定开发密钥对已就绪)", kid);
        } catch (Exception e) {
            log.error("[RSA密钥初始化失败]", e);
            throw new IllegalStateException("无法初始化 RSA 密钥对: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前 RSA JWK 对象（含私钥）。
     *
     * @return RSAKey 实例
     */
    public RSAKey getRsaJwk() {
        return rsaJwk;
    }

    /**
     * 获取 RSA 公钥。
     *
     * @return RSAPublicKey
     */
    public RSAPublicKey getPublicKey() {
        try {
            return rsaJwk.toRSAPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("获取 RSA 公钥失败", e);
        }
    }

    /**
     * 获取 RSA 私钥。
     *
     * @return RSAPrivateKey
     */
    public RSAPrivateKey getPrivateKey() {
        try {
            return rsaJwk.toRSAPrivateKey();
        } catch (Exception e) {
            throw new IllegalStateException("获取 RSA 私钥失败", e);
        }
    }

    /**
     * 获取标准 JWKS (JSON Web Key Set) 对象。
     *
     * @return 包含公钥的 JWKSet
     */
    public JWKSet getPublicJwkSet() {
        return new JWKSet(rsaJwk.toPublicJWK());
    }

    /**
     * 获取暴露给网关/客户端校验的 JWKS JSON 格式数据。
     *
     * @return 符合 RFC 7517 的 JSON Map
     */
    public Map<String, Object> getJwksJson() {
        return getPublicJwkSet().toJSONObject();
    }

    /**
     * 获取 JWS 签名器。
     *
     * @return JWSSigner 实例
     */
    public JWSSigner getSigner() {
        return signer;
    }

    /**
     * 获取 JWS 验签器。
     *
     * @return JWSVerifier 实例
     */
    public JWSVerifier getVerifier() {
        return verifier;
    }
}
