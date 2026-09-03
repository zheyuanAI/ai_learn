package com.ailearn.platform.auth.domain.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 租户后台管理视图对象 VO。
 * <p>
 * 封装租户的基础信息、唯一编码、当前运营状态与审计时间。
 * </p>
 */
@Schema(description = "租户后台管理详情对象")
public class TenantAdminVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户唯一标识 ID
     */
    @Schema(description = "租户唯一标识 ID", example = "a0000000-0000-0000-0000-000000000001")
    private UUID id;

    /**
     * 租户业务编码（全局唯一）
     */
    @Schema(description = "租户唯一业务编码", example = "DEFAULT")
    private String tenantCode;

    /**
     * 租户名称
     */
    @Schema(description = "租户展示名称", example = "示范智能制造工厂")
    private String tenantName;

    /**
     * 租户状态（ACTIVE: 正常, DISABLED: 禁用）
     */
    @Schema(description = "租户状态 (ACTIVE / DISABLED)", example = "ACTIVE")
    private String status;

    /**
     * 创建人
     */
    @Schema(description = "创建人账号", example = "system")
    private String createdBy;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-08-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 最后更新人
     */
    @Schema(description = "最后更新人", example = "admin.zhang")
    private String updatedBy;

    /**
     * 最后更新时间
     */
    @Schema(description = "最后更新时间", example = "2026-08-02 15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    public TenantAdminVo() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
