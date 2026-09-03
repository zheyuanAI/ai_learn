package com.ailearn.platform.auth.domain.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 权限点后台管理视图对象 VO。
 * <p>
 * 封装系统功能权限点的业务标识、所属模块、展示名称与说明。
 * </p>
 */
@Schema(description = "权限点后台管理详情对象")
public class PermissionAdminVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限点唯一标识 ID
     */
    @Schema(description = "权限点唯一标识 ID", example = "50000000-0000-0000-0000-000000000101")
    private UUID id;

    /**
     * 权限点唯一字符串标识（冒号格式，如 auth:user:view）
     */
    @Schema(description = "权限标识串 (冒号分段)", example = "auth:user:view")
    private String permissionCode;

    /**
     * 权限点名称
     */
    @Schema(description = "权限点展示名称", example = "用户查询")
    private String permissionName;

    /**
     * 所属业务模块（如 purchasing, sales, inventory, mes, iot, auth）
     */
    @Schema(description = "所属业务模块", example = "auth")
    private String module;

    /**
     * 权限点功能说明
     */
    @Schema(description = "权限功能详细说明", example = "查询用户列表与账号详情")
    private String description;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-08-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2026-08-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    public PermissionAdminVo() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
