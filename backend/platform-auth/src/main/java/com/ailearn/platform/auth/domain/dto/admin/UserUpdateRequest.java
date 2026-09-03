package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * 修改用户账号基本信息请求 DTO。
 * <p>
 * 封装用户的工号、真实姓名、联系方式及可选的角色重分配（账号名与租户禁止修改）。
 * </p>
 */
@Schema(description = "修改用户账号基本信息请求参数")
public class UserUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 员工工号/编号
     */
    @Schema(description = "员工工号 (租户内唯一)", example = "EMP1008")
    @Size(max = 64, message = "工号长度不能超过 64 个字符")
    private String userNo;

    /**
     * 用户真实姓名
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
     * 分配的角色 ID 列表（若为 null 则不更新角色）
     */
    @Schema(description = "分配的角色 ID 列表 (传入时将全量替换)")
    private List<UUID> roleIds;

    public UserUpdateRequest() {
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

    public List<UUID> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<UUID> roleIds) {
        this.roleIds = roleIds;
    }
}
