package com.ailearn.platform.auth.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 用户登录请求参数 DTO。
 * <p>
 * 接收客户端提交的所属租户编码、登录账号与密码。
 * </p>
 */
@Schema(description = "统一登录请求参数")
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "租户编码不能为空")
    @Schema(description = "所属租户编码", example = "DEFAULT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantCode;

    @NotBlank(message = "登录账号不能为空")
    @Schema(description = "登录账号名", example = "admin.zhang", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "登录密码不能为空")
    @Schema(description = "登录密码明文", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String tenantCode, String username, String password) {
        this.tenantCode = tenantCode;
        this.username = username;
        this.password = password;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
