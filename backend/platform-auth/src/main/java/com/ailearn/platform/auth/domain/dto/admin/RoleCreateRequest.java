package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 创建角色请求 DTO。
 * <p>
 * 封装新角色的唯一编码、展示名称、功能描述以及初始关联的权限点与菜单 ID 列表。
 * </p>
 */
@Schema(description = "创建角色请求参数")
public class RoleCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色唯一编码（租户内唯一，例如 QUALITY_AUDITOR）
     */
    @Schema(description = "角色业务编码 (租户内唯一)", example = "QUALITY_AUDITOR", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色编码不能为空")
    @Size(min = 2, max = 64, message = "角色编码长度必须在 2 到 64 个字符之间")
    private String roleCode;

    /**
     * 角色展示名称（例如 质量审计员）
     */
    @Schema(description = "角色展示名称", example = "质量审计员", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称长度不能超过 128 个字符")
    private String roleName;

    /**
     * 角色描述说明
     */
    @Schema(description = "角色描述说明", example = "负责原料与成品质检审计工作")
    @Size(max = 256, message = "角色描述长度不能超过 256 个字符")
    private String description;

    /**
     * 初始关联的权限点 ID 列表
     */
    @Schema(description = "初始分配的权限点 ID 列表")
    private List<UUID> permissionIds;

    /**
     * 初始关联的菜单 ID 列表
     */
    @Schema(description = "初始分配的菜单 ID 列表")
    private List<UUID> menuIds;

    public RoleCreateRequest() {
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<UUID> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }

    public List<UUID> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<UUID> menuIds) {
        this.menuIds = menuIds;
    }
}
