package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 角色状态变更请求 DTO。
 * <p>
 * 用于管理员启停用特定业务角色。
 * </p>
 */
@Schema(description = "角色状态变更请求参数")
public class RoleStatusUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标状态（ACTIVE: 正常, DISABLED: 禁用）
     */
    @Schema(description = "目标状态 (ACTIVE / DISABLED)", example = "DISABLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色状态不能为空")
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "状态必须为 ACTIVE 或 DISABLED")
    private String status;

    public RoleStatusUpdateRequest() {
    }

    public RoleStatusUpdateRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
