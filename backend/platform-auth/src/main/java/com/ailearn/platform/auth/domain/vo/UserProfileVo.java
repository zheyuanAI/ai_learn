package com.ailearn.platform.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 用户个人全量信息与权限上下文 VO（GET /api/me）。
 * <p>
 * 包含用户基础信息、分配的角色标识集、功能权限点集。
 * </p>
 */
@Schema(description = "当前登录用户全量信息与权限响应体")
public class UserProfileVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户唯一 ID")
    private UUID userId;

    @Schema(description = "所属租户 ID")
    private UUID tenantId;

    @Schema(description = "所属租户编码", example = "DEFAULT")
    private String tenantCode;

    @Schema(description = "登录账号名", example = "admin.zhang")
    private String username;

    @Schema(description = "用户真实姓名", example = "张管理员")
    private String realName;

    @Schema(description = "电子邮箱")
    private String email;

    @Schema(description = "联系手机号")
    private String phone;

    @Schema(description = "分配的角色编码列表", example = "[\"TENANT_ADMIN\"]")
    private List<String> roles;

    @Schema(description = "分配的功能权限点列表", example = "[\"sales.order.create\", \"inventory.balance.view\"]")
    private Set<String> perms;

    public UserProfileVo() {
        this.roles = Collections.emptyList();
        this.perms = Collections.emptySet();
    }

    public UserProfileVo(UUID userId, UUID tenantId, String tenantCode, String username, String realName, String email, String phone, List<String> roles, Set<String> perms) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.username = username;
        this.realName = realName;
        this.email = email;
        this.phone = phone;
        this.roles = roles != null ? roles : Collections.emptyList();
        this.perms = perms != null ? perms : Collections.emptySet();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles != null ? roles : Collections.emptyList();
    }

    public Set<String> getPerms() {
        return perms;
    }

    public void setPerms(Set<String> perms) {
        this.perms = perms != null ? perms : Collections.emptySet();
    }
}
