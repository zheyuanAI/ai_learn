package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 角色菜单分配请求 DTO。
 * <p>
 * 封装角色关联的菜单 ID 列表（全量替换）。
 * </p>
 */
@Schema(description = "角色菜单分配请求参数")
public class RoleMenusAssignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单 ID 列表
     */
    @Schema(description = "目标菜单 ID 列表 (传入空数组表示清空所有菜单)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "菜单 ID 列表不能为 null")
    private List<UUID> menuIds;

    public RoleMenusAssignRequest() {
    }

    public RoleMenusAssignRequest(List<UUID> menuIds) {
        this.menuIds = menuIds;
    }

    public List<UUID> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<UUID> menuIds) {
        this.menuIds = menuIds;
    }
}
