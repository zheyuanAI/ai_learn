package com.ailearn.platform.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 登录成功响应体 VO。
 * <p>
 * 返回签发的 RSA JWT Token、会话标识 JTI、有效时长及当前用户基本信息。
 * </p>
 */
@Schema(description = "统一登录成功响应体")
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "访问令牌（RSA 签名 JWT）", example = "eyJhbGciOiJSUzI1NiIs...")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "本次会话唯一标识 JTI", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String jti;

    @Schema(description = "令牌有效时间（秒）", example = "7200")
    private long expiresIn;

    @Schema(description = "登录用户信息")
    private UserInfoVo user;

    public LoginResponse() {
        this.tokenType = "Bearer";
    }

    public LoginResponse(String token, String jti, long expiresIn, UserInfoVo user) {
        this.token = token;
        this.tokenType = "Bearer";
        this.jti = jti;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserInfoVo getUser() {
        return user;
    }

    public void setUser(UserInfoVo user) {
        this.user = user;
    }
}
