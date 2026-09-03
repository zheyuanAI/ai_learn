package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 用户角色分配请求 DTO。
 * <p>
 * 用于为用户全量重新分配所属业务角色。
 * </p>
 */
@Schema(description = "用户角色分配请求参数")
public class UserRoleAssignRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID 列表（全量替换）
     */
    @Schema(description = "目标角色 ID 列表 (传入空数组表示清空所有角色)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "角色 ID 列表不能为 null")
    private List<UUID> roleIds;

    public UserRoleAssignRequest() {
    }

    public UserRoleAssignRequest(List<UUID> roleIds) {
        this.roleIds = roleIds;
    }

    public List<UUID> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<UUID> roleIds) {
        this.roleIds = roleIds;
    }
}
