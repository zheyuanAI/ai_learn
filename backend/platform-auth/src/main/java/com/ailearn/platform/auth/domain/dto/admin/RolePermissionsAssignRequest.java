package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 角色功能权限点分配请求 DTO。
 * <p>
 * 封装角色关联的功能权限点 ID 列表（全量替换）。
 * </p>
 */
@Schema(description = "角色功能权限点分配请求参数")
public class RolePermissionsAssignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限点 ID 列表
     */
    @Schema(description = "目标权限点 ID 列表 (传入空数组表示清空所有权限)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "权限点 ID 列表不能为 null")
    private List<UUID> permissionIds;

    public RolePermissionsAssignRequest() {
    }

    public RolePermissionsAssignRequest(List<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }

    public List<UUID> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<UUID> permissionIds) {
        this.permissionIds = permissionIds;
    }
}
