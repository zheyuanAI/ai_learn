package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 角色查询筛选请求 DTO。
 * <p>
 * 封装角色的模糊检索条件与状态筛选。
 * </p>
 */
@Schema(description = "角色查询筛选请求参数")
public class RoleQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色标识编码模糊搜索
     */
    @Schema(description = "角色业务编码模糊搜索", example = "sales")
    private String roleCode;

    /**
     * 角色名称模糊搜索
     */
    @Schema(description = "角色名称模糊搜索", example = "销售")
    private String roleName;

    /**
     * 状态精确筛选（ACTIVE / DISABLED）
     */
    @Schema(description = "角色状态 (ACTIVE / DISABLED)", example = "ACTIVE")
    private String status;

    public RoleQueryRequest() {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
