package com.ailearn.platform.auth.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.UUID;

/**
 * 基础用户信息 VO。
 */
@Schema(description = "基础用户信息响应体")
public class UserInfoVo implements Serializable {

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

    @Schema(description = "手机号码")
    private String phone;

    public UserInfoVo() {
    }

    public UserInfoVo(UUID userId, UUID tenantId, String tenantCode, String username, String realName, String email, String phone) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.username = username;
        this.realName = realName;
        this.email = email;
        this.phone = phone;
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
}
