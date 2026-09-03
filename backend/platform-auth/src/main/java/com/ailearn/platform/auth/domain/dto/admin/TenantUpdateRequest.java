package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 * 租户信息修改请求 DTO。
 * <p>
 * 用于管理员修改当前租户的企业名称与运营状态。
 * </p>
 */
@Schema(description = "租户信息修改请求参数")
public class TenantUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户展示名称
     */
    @Schema(description = "租户名称", example = "华东智能制造二号工厂", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户名称不能为空")
    @Size(max = 128, message = "租户名称长度不能超过 128 个字符")
    private String tenantName;

    /**
     * 租户状态（ACTIVE: 正常, DISABLED: 停用）
     */
    @Schema(description = "租户状态 (ACTIVE / DISABLED)", example = "ACTIVE")
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "租户状态只能为 ACTIVE 或 DISABLED")
    private String status;

    public TenantUpdateRequest() {
    }

    public TenantUpdateRequest(String tenantName, String status) {
        this.tenantName = tenantName;
        this.status = status;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
