package com.ailearn.platform.auth.domain.vo.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 菜单后台管理树节点视图对象 VO。
 * <p>
 * 封装菜单节点的前端路由、组件路径、图标、排序权重及嵌套的下级子菜单集合。
 * </p>
 */
@Schema(description = "菜单后台管理树节点对象")
public class MenuAdminNodeVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单唯一标识 ID
     */
    @Schema(description = "菜单唯一标识 ID", example = "e0000000-0000-0000-0000-000000000009")
    private UUID id;

    /**
     * 父级菜单 ID（顶级节点为 null）
     */
    @Schema(description = "父级菜单 ID (顶级节点为 null)", example = "null")
    private UUID parentId;

    /**
     * 菜单业务编码
     */
    @Schema(description = "菜单业务编码", example = "system")
    private String menuCode;

    /**
     * 菜单显示名称
     */
    @Schema(description = "菜单展示名称", example = "系统管理")
    private String menuName;

    /**
     * 前端路由路径
     */
    @Schema(description = "前端路由路径", example = "/system")
    private String routePath;

    /**
     * 前端组件文件路径
     */
    @Schema(description = "前端组件路径", example = "views/System.vue")
    private String componentPath;

    /**
     * 图标样式标识
     */
    @Schema(description = "图标标识", example = "SettingOutlined")
    private String icon;

    /**
     * 同级排序权重（数值越小排序越靠前）
     */
    @Schema(description = "同级排序权重 (升序)", example = "9")
    private Integer sortOrder;

    /**
     * 关联权限点编码
     */
    @Schema(description = "关联权限点编码", example = "auth:menu:view")
    private String permissionCode;

    /**
     * 是否在侧边栏可见
     */
    @Schema(description = "是否在侧边栏可见", example = "true")
    private Boolean visible;

    /**
     * 菜单启用状态（ACTIVE: 启用，DISABLED: 停用）
     */
    @Schema(description = "菜单启用状态", example = "ACTIVE", allowableValues = {"ACTIVE", "DISABLED"})
    private String status;

    /**
     * 下级子菜单列表
     */
    @Schema(description = "下级子菜单列表")
    private List<MenuAdminNodeVo> children;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-08-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2026-08-02 15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    public MenuAdminNodeVo() {
        this.children = new ArrayList<>();
        this.visible = true;
        this.status = "ACTIVE";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<MenuAdminNodeVo> getChildren() {
        return children;
    }

    public void setChildren(List<MenuAdminNodeVo> children) {
        this.children = children;
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
