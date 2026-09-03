package com.ailearn.platform.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 动态菜单树节点 VO（GET /api/me/menus）。
 * <p>
 * 按层级组织的前端路由与导航菜单树结构。
 * </p>
 */
@Schema(description = "动态菜单树节点响应体")
public class MenuNodeVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "菜单 ID")
    private UUID id;

    @Schema(description = "父菜单 ID，根节点为 null")
    private UUID parentId;

    @Schema(description = "菜单业务编码", example = "sales-outbound")
    private String menuCode;

    @Schema(description = "菜单展示名称", example = "销售交付")
    private String menuName;

    @Schema(description = "前端路由路径", example = "/sales-outbound")
    private String routePath;

    @Schema(description = "前端视图组件路径", example = "views/SalesOutbound.vue")
    private String componentPath;

    @Schema(description = "图标标识", example = "icon-shopping-cart")
    private String icon;

    @Schema(description = "排序序号", example = "10")
    private Integer sortOrder;

    @Schema(description = "是否在侧边栏可见", example = "true")
    private Boolean visible;

    @Schema(description = "菜单启用状态", example = "ACTIVE", allowableValues = {"ACTIVE", "DISABLED"})
    private String status;

    @Schema(description = "子菜单节点列表")
    private List<MenuNodeVo> children = new ArrayList<>();

    public MenuNodeVo() {
        this.children = new ArrayList<>();
    }

    public MenuNodeVo(UUID id, UUID parentId, String menuCode, String menuName, String routePath, String componentPath, String icon, Integer sortOrder, Boolean visible) {
        this.id = id;
        this.parentId = parentId;
        this.menuCode = menuCode;
        this.menuName = menuName;
        this.routePath = routePath;
        this.componentPath = componentPath;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.visible = visible;
        this.children = new ArrayList<>();
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
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getVisible() {
        return visible;
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

    public List<MenuNodeVo> getChildren() {
        return children;
    }

    public void setChildren(List<MenuNodeVo> children) {
        this.children = children != null ? children : new ArrayList<>();
    }

    /**
     * 添加子菜单项。
     *
     * @param child 子菜单节点
     */
    public void addChild(MenuNodeVo child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
