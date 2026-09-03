package com.ailearn.platform.shared.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 本地开发与测试环境专用的稳定 RSA 2048 位密钥对常量。
 * <p>
 * <b>重要提示：</b> 本密钥对仅供本地开发、单机调试及单元/集成测试使用，严禁在生产环境中作为安全密钥！
 * 生产环境必须通过环境变量 AUTH_JWT_PRIVATE_KEY / GATEWAY_JWT_PUBLIC_KEY 注入独立生成的受保护密钥。
 * </p>
 */
public final class DevRsaKeyDefaults {

    private DevRsaKeyDefaults() {
    }

    /**
     * 稳定固定 Key ID (kid)
     */
    public static final String DEV_KEY_ID = "ai-learn-auth-key-1";

    /**
     * 开发环境公钥 PEM（非生产）
     */
    public static final String DEV_PUBLIC_KEY_PEM =
            "-----BEGIN PUBLIC KEY-----\n" +
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvG/ElB7VTSWUBlY7H1GH\n" +
            "UPYUyvnu6LV3p+zp4bryeOtFQSLUy+K3Skufv+cRhur58Gwat1i8onofGnD5dhSU\n" +
            "laAD3WyE+oopzTA8g49+JV2XH4QZMMlMkDRFNFfNIML78vZBvCShwM1YvEczJPwG\n" +
            "VEeQSUHfEr2xfDBgsRoYOITuDv25RoCIJ5rlKouuSDymjZLhfz/P9mKdaDKHnY0t\n" +
            "s2z0I1Q1oR/6D5yqUOLNNqPNpnsRdiDzM7CooscVXX5OOI9bZENa8OHGF3ht4yEr\n" +
            "i6NrjUSxd8mBAC4irxTbDFcEEP1fmZPxibNif49NNcCfvkfWy0YIMkCtP5dQQd+N\n" +
            "yQIDAQAB\n" +
            "-----END PUBLIC KEY-----";

    /**
     * 开发环境私钥 PEM（非生产）
     */
    public static final String DEV_PRIVATE_KEY_PEM =
            "-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC8b8SUHtVNJZQG\n" +
            "VjsfUYdQ9hTK+e7otXen7OnhuvJ460VBItTL4rdKS5+/5xGG6vnwbBq3WLyieh8a\n" +
            "cPl2FJSVoAPdbIT6iinNMDyDj34lXZcfhBkwyUyQNEU0V80gwvvy9kG8JKHAzVi8\n" +
            "RzMk/AZUR5BJQd8SvbF8MGCxGhg4hO4O/blGgIgnmuUqi65IPKaNkuF/P8/2Yp1o\n" +
            "MoedjS2zbPQjVDWhH/oPnKpQ4s02o82mexF2IPMzsKiixxVdfk44j1tkQ1rw4cYX\n" +
            "eG3jISuLo2uNRLF3yYEALiKvFNsMVwQQ/V+Zk/GJs2J/j001wJ++R9bLRggyQK0/\n" +
            "l1BB343JAgMBAAECggEAAwrabYpMZxcX21zVzkkEkmjwt8C+i90PMiPD+HAqZUum\n" +
            "Jfm0HhkaXL2ZFhH64Vbmo2w5Z7Or8wMeALEpg++r4PkRCuiLQpjLuTuBxJ2C28NV\n" +
            "su1w8y3S2cKwSdqvjgt74iW59LnSL0DZgYu0PpD/oKl5occpnP25UYAd9YoOcU4k\n" +
            "BcrF2p183g5lkbjnzV5MiB/lmF3M46feScF7LRKKZ0nmGJY/LrI/Z1YHbHS6PMyN\n" +
            "eraHx41KqpF9xpxyaOMcC/QpNn4Ut1Vf/Y7pSnOgRQJ6F9x7FgWgelo07b1vejvl\n" +
            "n1nNkR9fuHZDF3tnAzJgKpEKxTrjQUaQWKY8ON0G0QKBgQDsXf7a9fGOoqEsNZTg\n" +
            "u/HF5322wdE5tGQQzL5Lr6M+t5iAK56YxlPYRlRsnVFrbh4TpcLwKcsOwMAo3/30\n" +
            "/Nac51bSo7h1GbCNz3CARgIkNmu9vI//YDncoYduRHME9bOIfzaDlvodhpMMknMV\n" +
            "uUsgL8ipXLt5umlzb7my3Y9ScQKBgQDMFpkorVG9Df/Uz7Y63iIyHAOiQPr//rvg\n" +
            "+64S2PbK0zt0my0YSWjxlxiyroqXVfSOqXfeBQmZXH3rPz0hYq02dU9bYciC2TJK\n" +
            "aZ+hEt/E26UR/HqFwZ8vwsx//U8xOkOW2TJUbBmULjIDqz6DP8J/a12Or27T/FCm\n" +
            "HjvR7cts2QKBgDpAI+mkSOaE78FsZwdHahsCpLmgZEabTTnSq2cNnuZ66otTtJ8j\n" +
            "6U/YbT/jOUiHd8QRysvTEObO28x2/ygcE2vRm1UH4hKBxT/9ilUia66u9rhouvgN\n" +
            "p9TWvgCPv+TOBHO0HiQp1fPm0WB8yv3kjz4caJBMeStTpF89fn81GPgRAoGBAKiO\n" +
            "dXp6AwtAd63cy2W9NsLqDjpArs0lJyi+1NsuJE4JvY4l502qu6jkoPpaW4536F63\n" +
            "Ko/M20u1by4O8b8UD3jt1Ffl38Pp/LYmnDddLkEZIzFCtDKqYn4AxgNjQ7elplL9\n" +
            "GatOCKYwrRAIq72cuFeiZgfFbqRYPIemasOMQTmpAoGBAK1KBPDcwrvAE+pTTKZL\n" +
            "rzWfqj3XSc7Yvudht5Atq02WVsvjczVJ+YGbMbXcxSuwT9LHlPdLGwSbwHIdKZ43\n" +
            "o74q3PDCMf4qYLqjTjN58NiFwe35D2ODWz+Hu/OmSz4sZF4/V5gg6yUOb1rfmpHf\n" +
            "7kqok+ff06RQQfizTV9pXrYZ\n" +
            "-----END PRIVATE KEY-----";

    /**
     * 加载开发环境默认 RSA 公钥对象。
     *
     * @return RSA 公钥
     */
    public static RSAPublicKey loadDevPublicKey() {
        try {
            String key = DEV_PUBLIC_KEY_PEM
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(key);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("加载开发环境默认 RSA 公钥失败", e);
        }
    }

    /**
     * 加载开发环境默认 RSA 私钥对象。
     *
     * @return RSA 私钥
     */
    public static RSAPrivateKey loadDevPrivateKey() {
        try {
            String key = DEV_PRIVATE_KEY_PEM
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("加载开发环境默认 RSA 私钥失败", e);
        }
    }
}
