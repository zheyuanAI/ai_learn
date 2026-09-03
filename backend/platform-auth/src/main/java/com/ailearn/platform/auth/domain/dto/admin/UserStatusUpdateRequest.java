package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 用户账号状态变更请求 DTO。
 * <p>
 * 用于管理员启停用或锁定指定用户账号。
 * </p>
 */
@Schema(description = "用户账号状态变更请求参数")
public class UserStatusUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标状态（ACTIVE: 正常, DISABLED: 禁用, LOCKED: 锁定）
     */
    @Schema(description = "目标状态 (ACTIVE / DISABLED / LOCKED)", example = "DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ACTIVE|DISABLED|LOCKED)$", message = "状态必须为 ACTIVE、DISABLED 或 LOCKED")
    private String status;

    public UserStatusUpdateRequest() {
    }

    public UserStatusUpdateRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
