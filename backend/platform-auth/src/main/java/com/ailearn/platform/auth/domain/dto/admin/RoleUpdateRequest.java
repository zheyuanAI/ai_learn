package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 修改角色基本信息请求 DTO。
 * <p>
 * 封装角色的名称、描述更新，以及可选的权限与菜单重分配（角色编码禁止随意变更）。
 * </p>
 */
@Schema(description = "修改角色基本信息请求参数")
public class RoleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色展示名称
     */
    @Schema(description = "角色展示名称", example = "资深销售人员", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称长度不能超过 128 个字符")
    private String roleName;

    /**
     * 角色功能描述
     */
    @Schema(description = "角色描述说明", example = "负责重要客户销售订单管理")
    @Size(max = 256, message = "角色描述长度不能超过 256 个字符")
    private String description;

    /**
     * 重新分配的权限点 ID 列表（若为 null 则保持不变）
     */
    @Schema(description = "重新分配的权限点 ID 列表 (传入时将全量替换)")
    private List<UUID> permissionIds;

    /**
     * 重新分配的菜单 ID 列表（若为 null 则保持不变）
     */
    @Schema(description = "重新分配的菜单 ID 列表 (传入时将全量替换)")
    private List<UUID> menuIds;

    public RoleUpdateRequest() {
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
