package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

/**
 * 创建菜单节点请求 DTO。
 * <p>
 * 封装新建系统菜单的基础属性、前端路由组件、图标与排序序号。
 * </p>
 */
@Schema(description = "创建菜单节点请求参数")
public class MenuCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 父级菜单 ID（顶级菜单传 null）
     */
    @Schema(description = "父级菜单 ID (顶级菜单为 null)", example = "e0000000-0000-0000-0000-000000000009")
    private UUID parentId;

    /**
     * 菜单唯一标识编码（例如 sys_custom）
     */
    @Schema(description = "菜单唯一标识编码", example = "sys_custom", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜单编码不能为空")
    @Size(min = 2, max = 64, message = "菜单编码长度必须在 2 到 64 个字符之间")
    private String menuCode;

    /**
     * 菜单展示名称
     */
    @Schema(description = "菜单展示名称", example = "自定义管理", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 128, message = "菜单名称长度不能超过 128 个字符")
    private String menuName;

    /**
     * 前端路由路径（例如 /system/custom）
     */
    @Schema(description = "前端路由路径", example = "/system/custom")
    @Size(max = 255, message = "路由路径长度不能超过 255 个字符")
    private String routePath;

    /**
     * 前端视图组件路径（例如 views/system/CustomList.vue）
     */
    @Schema(description = "前端视图组件路径", example = "views/system/CustomList.vue")
    @Size(max = 255, message = "组件路径长度不能超过 255 个字符")
    private String componentPath;

    /**
     * 菜单图标标识（例如 SettingOutlined）
     */
    @Schema(description = "菜单图标标识", example = "SettingOutlined")
    @Size(max = 64, message = "图标标识长度不能超过 64 个字符")
    private String icon;

    /**
     * 同级菜单排序序号（升序）
     */
    @Schema(description = "同级菜单排序序号 (越小越靠前)", example = "10")
    private Integer sortOrder = 0;

    /**
     * 关联权限点编码（可选）
     */
    @Schema(description = "关联权限点编码", example = "auth:role:view")
    @Size(max = 100, message = "权限编码长度不能超过 100 个字符")
    private String permissionCode;

    /**
     * 是否在侧边栏可见
     */
    @Schema(description = "是否在侧边栏可见 (默认 true)", example = "true")
    private Boolean visible = true;

    public MenuCreateRequest() {
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
        return visible != null ? visible : true;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }
}
