package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 权限点列表查询请求 DTO。
 * <p>
 * 封装按所属业务模块、权限编码与权限名称的模糊/精确查询条件。
 * </p>
 */
@Schema(description = "权限点列表查询请求参数")
public class PermissionQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 所属业务模块（如 purchasing, sales, inventory, mes, iot, auth）
     */
    @Schema(description = "所属业务模块", example = "sales")
    private String module;

    /**
     * 权限标识编码模糊搜索
     */
    @Schema(description = "权限编码模糊搜索", example = "sales:order")
    private String permissionCode;

    /**
     * 权限名称模糊搜索
     */
    @Schema(description = "权限名称模糊搜索", example = "销售")
    private String permissionName;

    public PermissionQueryRequest() {
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
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
}
