package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

/**
 * 修改菜单节点属性请求 DTO。
 * <p>
 * 封装菜单父级节点变更、展示名称、前端路由与视图组件、排序及可见性更新。
 * </p>
 */
@Schema(description = "修改菜单节点属性请求参数")
public class MenuUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 父级菜单 ID（顶级菜单传 null）
     */
    @Schema(description = "父级菜单 ID (顶级菜单为 null)", example = "e0000000-0000-0000-0000-000000000009")
    private UUID parentId;

    /**
     * 菜单业务编码（当前租户内唯一，可在编辑时修改）
     */
    @Schema(description = "菜单业务编码", example = "sys_user", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜单编码不能为空")
    @Size(min = 2, max = 64, message = "菜单编码长度必须在 2 到 64 个字符之间")
    private String menuCode;

    /**
     * 菜单展示名称
     */
    @Schema(description = "菜单展示名称", example = "用户管理", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 128, message = "菜单名称长度不能超过 128 个字符")
    private String menuName;

    /**
     * 前端路由路径
     */
    @Schema(description = "前端路由路径", example = "/system/users")
    @Size(max = 255, message = "路由路径长度不能超过 255 个字符")
    private String routePath;

    /**
     * 前端视图组件路径
     */
    @Schema(description = "前端视图组件路径", example = "views/system/UserList.vue")
    @Size(max = 255, message = "组件路径长度不能超过 255 个字符")
    private String componentPath;

    /**
     * 菜单图标标识
     */
    @Schema(description = "菜单图标标识", example = "UserOutlined")
    @Size(max = 64, message = "图标标识长度不能超过 64 个字符")
    private String icon;

    /**
     * 同级菜单排序序号（升序）
     */
    @Schema(description = "同级菜单排序序号", example = "1")
    private Integer sortOrder;

    /**
     * 关联权限点编码（可选）
     */
    @Schema(description = "关联权限点编码", example = "auth:user:view")
    @Size(max = 100, message = "权限编码长度不能超过 100 个字符")
    private String permissionCode;

    /**
     * 是否在侧边栏可见
     */
    @Schema(description = "是否在侧边栏可见", example = "true")
    private Boolean visible;

    public MenuUpdateRequest() {
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getMenuCode() {
        return menuCode;
    }

    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getComponentPath() {
        return componentPath;
    }

    public void setComponentPath(String componentPath) {
        this.componentPath = componentPath;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return sortOrder != null ? sortOrder : 0;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }
}
