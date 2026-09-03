package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 新增用户账号请求 DTO。
 * <p>
 * 封装新用户的登录名、初始明文密码、工号、真实姓名、联系方式与初始角色分配。
 * </p>
 */
@Schema(description = "新增用户账号请求参数")
public class UserCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 登录账号名（租户内唯一）
     */
    @Schema(description = "登录账号名 (租户内唯一)", example = "operator.li", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录账号不能为空")
    @Size(min = 2, max = 64, message = "登录账号长度必须在 2 到 64 个字符之间")
    private String username;

    /**
     * 初始登录密码（明文，落库前将通过 BCrypt 强哈希）
     */
    @Schema(description = "初始登录密码 (至少6位)", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "登录密码不能为空")
    @Size(min = 6, max = 64, message = "登录密码长度必须在 6 到 64 个字符之间")
    private String password;

    /**
     * 员工工号/编号（租户内唯一）
     */
    @Schema(description = "员工工号 (租户内唯一)", example = "EMP1008")
    @Size(max = 64, message = "工号长度不能超过 64 个字符")
    private String userNo;

    /**
     * 真实姓名
     */
    @Schema(description = "用户真实姓名", example = "李操作员", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户真实姓名不能为空")
    @Size(max = 64, message = "真实姓名长度不能超过 64 个字符")
    private String realName;

    /**
     * 电子邮箱
     */
    @Schema(description = "电子邮箱", example = "operator.li@example.com")
    @Email(message = "电子邮箱格式不正确")
    @Size(max = 128, message = "电子邮箱长度不能超过 128 个字符")
    private String email;

    /**
     * 联系电话
     */
    @Schema(description = "联系手机号", example = "13900000008")
    @Size(max = 32, message = "联系电话长度不能超过 32 个字符")
    private String phone;

    /**
     * 初始账号状态（默认 ACTIVE）
     */
    @Schema(description = "账号初始状态 (ACTIVE / DISABLED / LOCKED)", example = "ACTIVE")
    @Pattern(regexp = "^(ACTIVE|DISABLED|LOCKED)$", message = "状态必须为 ACTIVE、DISABLED 或 LOCKED")
    private String status = "ACTIVE";

    /**
     * 分配的角色 ID 列表
     */
    @Schema(description = "分配的角色 ID 列表")
    private List<UUID> roleIds;

    public UserCreateRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserNo() {
        return userNo;
    }

    public void setUserNo(String userNo) {
        this.userNo = userNo;
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

    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<UUID> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<UUID> roleIds) {
        this.roleIds = roleIds;
    }
}
