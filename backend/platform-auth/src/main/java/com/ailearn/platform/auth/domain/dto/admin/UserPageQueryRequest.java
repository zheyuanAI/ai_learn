package com.ailearn.platform.auth.domain.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.UUID;

/**
 * 用户列表分页检索请求 DTO。
 * <p>
 * 封装分页参数及多维度模糊/精确查询条件（用户名、姓名、工号、状态、关联角色等）。
 * </p>
 */
@Schema(description = "用户列表分页检索请求参数")
public class UserPageQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码（从 1 开始）
     */
    @Schema(description = "页码 (默认 1)", example = "1")
    @Min(value = 1, message = "页码必须大于 0")
    private Integer page = 1;

    /**
     * 每页大小（1 ~ 100）
     */
    @Schema(description = "每页记录数 (默认 10, 最大 100)", example = "10")
    @Min(value = 1, message = "每页记录数必须大于 0")
    @Max(value = 100, message = "每页记录数最大支持 100 条")
    private Integer size = 10;

    /**
     * 登录账号模糊搜索
     */
    @Schema(description = "登录账号模糊搜索", example = "admin")
    private String username;

    /**
     * 用户真实姓名模糊搜索
     */
    @Schema(description = "真实姓名模糊搜索", example = "张管理")
    private String realName;

    /**
     * 工号模糊搜索
     */
    @Schema(description = "员工工号模糊搜索", example = "EMP001")
    private String userNo;

    /**
     * 账号状态精确筛选（ACTIVE, DISABLED, LOCKED）
     */
    @Schema(description = "账号状态 (ACTIVE, DISABLED, LOCKED)", example = "ACTIVE")
    private String status;

    /**
     * 所属角色 ID 精确筛选
     */
    @Schema(description = "所属角色 ID 精确筛选", example = "20000000-0000-0000-0000-000000000001")
    private UUID roleId;

    public UserPageQueryRequest() {
    }

    public Integer getPage() {
        return page != null ? page : 1;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size != null ? size : 10;
    }

    public void setSize(Integer size) {
        this.size = size;
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

    public String getUserNo() {
        return userNo;
    }

    public void setUserNo(String userNo) {
        this.userNo = userNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }
}
