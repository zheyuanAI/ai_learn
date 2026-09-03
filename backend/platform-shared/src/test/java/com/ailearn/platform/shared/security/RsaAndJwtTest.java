package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.context.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RSA 与 JWT 工具测试")
class RsaAndJwtTest {

    @Test
    @DisplayName("测试 RSA 密钥对生成、PEM 互转与签名验签")
    void testRsaKeyUtils() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair(2048);
        assertNotNull(keyPair);

        String pubPem = RsaKeyUtils.toPem(keyPair.getPublic());
        String priPem = RsaKeyUtils.toPem(keyPair.getPrivate());

        assertTrue(pubPem.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(priPem.contains("-----BEGIN PRIVATE KEY-----"));

        RSAPublicKey parsedPub = RsaKeyUtils.parsePublicKey(pubPem);
        RSAPrivateKey parsedPri = RsaKeyUtils.parsePrivateKey(priPem);

        assertNotNull(parsedPub);
        assertNotNull(parsedPri);

        String testData = "ai-learn-wms-security-test-data";
        String signature = RsaKeyUtils.signText(testData, parsedPri);
        assertNotNull(signature);

        boolean verified = RsaKeyUtils.verifyText(testData, signature, parsedPub);
        assertTrue(verified);

        boolean falsified = RsaKeyUtils.verifyText(testData + "_tampered", signature, parsedPub);
        assertFalse(falsified);
    }

    @Test
    @DisplayName("测试 JWT Token 签发与上下文还原")
    void testJwtKeyProvider() {
        KeyPair keyPair = RsaKeyUtils.generateKeyPair(2048);
        JwtKeyProvider provider = new JwtKeyProvider(keyPair);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String username = "admin";
        String jti = UUID.randomUUID().toString();
        List<String> roles = List.of("TENANT_ADMIN", "WAREHOUSE");
        List<String> permissions = List.of("purchase:receipt:confirm", "inventory:view");

        String token = provider.createToken(tenantId, userId, username, jti, roles, permissions, Duration.ofMinutes(30));
        assertNotNull(token);

        RequestContext context = provider.parseRequestContext(token);
        assertEquals(tenantId, context.getTenantId());
        assertEquals(userId, context.getUserId());
        assertEquals(username, context.getUsername());
        assertEquals(jti, context.getJti());
        assertTrue(context.hasRole("TENANT_ADMIN"));
        assertTrue(context.hasRole("WAREHOUSE"));
        assertTrue(context.hasPermission("purchase:receipt:confirm"));
        assertFalse(context.hasPermission("sales:delete"));
    }
}
