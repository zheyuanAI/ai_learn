package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 菜单启用状态更新请求 DTO。
 * <p>
 * 用于快速切换菜单 ACTIVE/DISABLED 状态；visible 显隐属性由普通菜单更新接口维护。
 * </p>
 */
@Schema(description = "菜单启用状态更新请求参数")
public class MenuStatusUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单启用状态
     */
    @Schema(description = "菜单启用状态", example = "ACTIVE", allowableValues = {"ACTIVE", "DISABLED"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜单状态不能为空")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "菜单状态只能是 ACTIVE 或 DISABLED")
    private String status;

    public MenuStatusUpdateRequest() {
    }

    public MenuStatusUpdateRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
