package com.ailearn.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 菜单实体（auth_menu）。
 * <p>
 * 定义系统动态菜单树的路由、组件路径、图标与展示排序。
 * </p>
 */
@TableName("auth_menu")
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单唯一标识 ID
     */
    @TableId(type = IdType.INPUT)
    private UUID id;

    /**
     * 父菜单 ID（根节点为 null）
     */
    @TableField("parent_id")
    private UUID parentId;

    /**
     * 菜单唯一标识编码
     */
    @TableField("menu_code")
    private String menuCode;

    /**
     * 菜单展示名称
     */
    @TableField("menu_name")
    private String menuName;

    /**
     * 前端路由路径（例如 /dashboard, /erp-wms）
     */
    @TableField("route_path")
    private String routePath;

    /**
     * 前端视图组件路径（例如 views/Dashboard.vue）
     */
    @TableField("component_path")
    private String componentPath;

    /**
     * 菜单图标标识（例如 icon-dashboard）
     */
    @TableField("icon")
    private String icon;

    /**
     * 同级菜单排序序号（数值越小排序越靠前）
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 关联权限点编码
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 所属租户 ID（多租户物理隔离字段）
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 是否在侧边栏可见（TRUE: 可见, FALSE: 隐藏）
     */
    @TableField("visible")
    private Boolean visible;

    /**
     * 菜单状态（ACTIVE: 启用, DISABLED: 停用）
     */
    @TableField("status")
    private String status;

    /**
     * 创建时间
     */
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标识（0: 未删除, 1: 已删除）
     */
    @TableLogic
    @TableField("isdel")
    private Integer isdel;

    public Menu() {
        this.sortOrder = 0;
        this.visible = true;
        this.status = "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Menu(UUID id, UUID parentId, String menuCode, String menuName, String routePath, String componentPath, String icon, Integer sortOrder, String permissionCode) {
        this.id = id != null ? id : UUID.randomUUID();
        this.parentId = parentId;
        this.menuCode = menuCode;
        this.menuName = menuName;
        this.routePath = routePath;
        this.componentPath = componentPath;
        this.icon = icon;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.permissionCode = permissionCode;
        this.visible = true;
        this.status = "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Menu(UUID id, UUID parentId, UUID tenantId, String menuCode, String menuName, String routePath, String componentPath, String icon, Integer sortOrder, String permissionCode, Boolean visible, String status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.parentId = parentId;
        this.tenantId = tenantId;
        this.menuCode = menuCode;
        this.menuName = menuName;
        this.routePath = routePath;
        this.componentPath = componentPath;
        this.icon = icon;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.permissionCode = permissionCode;
        this.visible = visible != null ? visible : true;
        this.status = status != null ? status : "ACTIVE";
        this.isdel = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    /**
     * 获取所属租户 ID。
     *
     * @return 租户 UUID
     */
    public UUID getTenantId() {
        return tenantId;
    }

    /**
     * 设置所属租户 ID。
     *
     * @param tenantId 租户 UUID
     */
    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 获取菜单在侧边栏是否可见。
     *
     * @return 可见性标记
     */
    public Boolean getVisible() {
        return visible != null ? visible : true;
    }

    /**
     * 设置菜单在侧边栏是否可见。
     *
     * @param visible 可见性标记
     */
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    /**
     * 获取菜单启用状态。
     *
     * @return 状态字符串 (ACTIVE/DISABLED)
     */
    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }

    /**
     * 设置菜单启用状态。
     *
     * @param status 状态字符串
     */
    public void setStatus(String status) {
        this.status = status;
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

    public Integer getIsdel() {
        return isdel;
    }

    public void setIsdel(Integer isdel) {
        this.isdel = isdel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Menu menu)) return false;
        return Objects.equals(id, menu.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
