package com.ailearn.platform.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 非对称加密与密钥对工具类。
 * <p>
 * 提供 RSA 密钥对生成、PEM/Base64 字符串解析、PEM 格式导出以及 SHA256withRSA 签名与验签能力。
 * </p>
 */
public final class RsaKeyUtils {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    private RsaKeyUtils() {
    }

    /**
     * 生成指定位数的 RSA 密钥对。
     *
     * @param keySize 密钥长度（推荐 2048 或 4096）
     * @return {@link KeyPair} 包含公钥与私钥
     */
    public static KeyPair generateKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(keySize);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 RSA 算法", e);
        }
    }

    /**
     * 生成默认 2048 位的 RSA 密钥对。
     *
     * @return 2048 位的 {@link KeyPair}
     */
    public static KeyPair generateKeyPair() {
        return generateKeyPair(2048);
    }

    /**
     * 解析 X.509 格式的 RSA 公钥（支持 PEM 格式或纯 Base64 格式）。
     *
     * @param publicKeyPemOrBase64 公钥 PEM 文本或 Base64 编码字符串
     * @return {@link RSAPublicKey} 公钥实例
     */
    public static RSAPublicKey parsePublicKey(String publicKeyPemOrBase64) {
        try {
            String cleanKey = cleanPemContent(publicKeyPemOrBase64, "PUBLIC KEY");
            byte[] decoded = Base64.getDecoder().decode(cleanKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return (RSAPublicKey) keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA 公钥解析失败，请检查 PEM/Base64 内容格式", e);
        }
    }

    /**
     * 解析 PKCS#8 格式的 RSA 私钥（支持 PEM 格式或纯 Base64 格式）。
     *
     * @param privateKeyPemOrBase64 私钥 PEM 文本或 Base64 编码字符串
     * @return {@link RSAPrivateKey} 私钥实例
     */
    public static RSAPrivateKey parsePrivateKey(String privateKeyPemOrBase64) {
        try {
            String cleanKey = cleanPemContent(privateKeyPemOrBase64, "PRIVATE KEY");
            byte[] decoded = Base64.getDecoder().decode(cleanKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return (RSAPrivateKey) keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA 私钥解析失败，请检查 PEM/Base64 内容格式", e);
        }
    }

    /**
     * 将公钥导出为标准 PEM 文本格式。
     *
     * @param publicKey RSA 公钥
     * @return 带有 -----BEGIN PUBLIC KEY----- 标头的 PEM 格式字符串
     */
    public static String toPem(PublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * 将私钥导出为标准 PEM 文本格式。
     *
     * @param privateKey RSA 私钥
     * @return 带有 -----BEGIN PRIVATE KEY----- 标头的 PEM 格式字符串
     */
    public static String toPem(PrivateKey privateKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    /**
     * 使用私钥对数据进行 SHA256withRSA 数字签名。
     *
     * @param data       原始字节数据
     * @param privateKey 签名私钥
     * @return 签名结果字节数组
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) {
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 数字签名计算失败", e);
        }
    }

    /**
     * 使用私钥对 UTF-8 字符串签名，返回 Base64 编码签名串。
     *
     * @param text       待签名文本
     * @param privateKey 签名私钥
     * @return Base64 签名串
     */
    public static String signText(String text, PrivateKey privateKey) {
        byte[] signBytes = sign(text.getBytes(StandardCharsets.UTF_8), privateKey);
        return Base64.getEncoder().encodeToString(signBytes);
    }

    /**
     * 使用公钥验证 SHA256withRSA 签名。
     *
     * @param data      原始数据
     * @param sign      数字签名
     * @param publicKey 验证公钥
     * @return 验签通过返回 true，否则返回 false
     */
    public static boolean verify(byte[] data, byte[] sign, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(sign);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使用公钥验证 Base64 文本签名。
     *
     * @param text         原始文本
     * @param base64Sign   Base64 签名串
     * @param publicKey    验证公钥
     * @return 验签通过返回 true，否则返回 false
     */
    public static boolean verifyText(String text, String base64Sign, PublicKey publicKey) {
        try {
            byte[] signBytes = Base64.getDecoder().decode(base64Sign);
            return verify(text.getBytes(StandardCharsets.UTF_8), signBytes, publicKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清理 PEM 标头和尾部标记，返回纯 Base64 内容。
     *
     * @param pemContent 原始内容
     * @param headerType 标头类型名称（如 "PUBLIC KEY" 或 "PRIVATE KEY"）
     * @return 纯净的 Base64 编码字符串
     */
    private static String cleanPemContent(String pemContent, String headerType) {
        return pemContent
                .replace("-----BEGIN " + headerType + "-----", "")
                .replace("-----END " + headerType + "-----", "")
                .replace("-----BEGIN RSA " + headerType + "-----", "")
                .replace("-----END RSA " + headerType + "-----", "")
                .replaceAll("\\s+", "");
    }
}
