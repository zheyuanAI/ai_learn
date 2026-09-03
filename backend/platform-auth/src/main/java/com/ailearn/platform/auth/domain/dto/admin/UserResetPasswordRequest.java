package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 重置用户密码请求 DTO。
 * <p>
 * 用于管理员强制重置目标用户的登录密码。
 * </p>
 */
@Schema(description = "重置用户密码请求参数")
public class UserResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新密码明文（落库前通过 BCrypt 强哈希）
     */
    @Schema(description = "新登录密码 (至少6位)", example = "654321", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度必须在 6 到 64 个字符之间")
    private String newPassword;

    public UserResetPasswordRequest() {
    }

    public UserResetPasswordRequest(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
