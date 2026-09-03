package com.ailearn.platform.auth.controller.admin;

import com.ailearn.platform.auth.domain.dto.admin.UserCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserPageQueryRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserResetPasswordRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserRoleAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.PageResult;
import com.ailearn.platform.auth.domain.vo.admin.UserAdminVo;
import com.ailearn.platform.auth.service.admin.UserAdminService;
import com.ailearn.platform.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户后台管理 REST 控制器。
 * <p>
 * 提供用户列表分页查询、详情读取、新增用户、修改基本信息、启停用/锁定、重置密码、角色重新分配与删除端点。
 * </p>
 */
@Tag(name = "用户后台管理", description = "提供租户内用户全生命周期 CRUD 与权限角色指派端点")
@RestController
@RequestMapping("/api/auth/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * 多条件分页查询租户用户列表。
     * <p>
     * 【用途】供管理后台用户管理表格展示与条件检索。
     * 主要入参：request (分页及筛选参数)；
     * 返回结果：ApiResponse&lt;PageResult&lt;UserAdminVo&gt;&gt; 用户分页列表；
     * 简要流程：调用 service.pageUsers(request) 执行分页联查与脱敏组装。
     * </p>
     *
     * @param request 分页检索请求参数
     * @return 统一响应包装的用户分页对象
     */
    @Operation(summary = "分页查询用户列表", description = "支持按登录账号、真实姓名、工号、状态及所属角色分页检索")
    @GetMapping
    public ApiResponse<PageResult<UserAdminVo>> pageUsers(@Valid UserPageQueryRequest request) {
        PageResult<UserAdminVo> pageResult = userAdminService.pageUsers(request);
        return ApiResponse.ok(pageResult);
    }

    /**
     * 查询指定用户的详细画像。
     * <p>
     * 【用途】供管理后台查看或编辑用户信息前读取详情。
     * 主要入参：userId (目标用户ID)；
     * 返回结果：ApiResponse&lt;UserAdminVo&gt; 用户详细画像；
     * 简要流程：调用 service.getUserDetail(userId) 读取详情并返回。
     * </p>
     *
     * @param userId 目标用户 ID
     * @return 统一响应包装的用户视图对象
     */
    @Operation(summary = "获取用户详情", description = "读取指定用户的基本画像、工号、状态与已分配角色列表")
    @GetMapping("/{userId}")
    public ApiResponse<UserAdminVo> getUserDetail(
            @Parameter(description = "用户唯一标识 ID", required = true)
            @PathVariable("userId") UUID userId) {
        UserAdminVo vo = userAdminService.getUserDetail(userId);
        return ApiResponse.ok(vo);
    }

    /**
     * 创建新用户账号。
     * <p>
     * 【用途】供管理员录入新员工账号并分配角色。
     * 主要入参：request (包含 username, password, realName, userNo 等)；
     * 返回结果：ApiResponse&lt;UserAdminVo&gt; 创建成功的用户画像；
     * 简要流程：参数校验 -> 调用 service.createUser(request) 完成 BCrypt 加密与持久化。
     * </p>
     *
     * @param request 用户创建请求体
     * @return 统一响应包装的创建后用户视图对象
     */
    @Operation(summary = "创建用户账号", description = "录入新账号与初始明文密码（自动执行 BCrypt 哈希加密），支持分配初始角色")
    @PostMapping
    public ApiResponse<UserAdminVo> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserAdminVo vo = userAdminService.createUser(request);
        return ApiResponse.ok(vo);
    }

    /**
     * 修改用户基本信息。
     * <p>
     * 【用途】供管理员修改用户的工号、姓名、联系方式与角色。
     * 主要入参：userId (目标用户ID), request (修改请求体)；
     * 返回结果：ApiResponse&lt;UserAdminVo&gt; 修改后的用户画像；
     * 简要流程：参数校验 -> 调用 service.updateUser(userId, request)。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 用户修改请求体
     * @return 统一响应包装的更新后用户视图对象
     */
    @Operation(summary = "修改用户基本信息", description = "更新指定用户的真实姓名、工号、联系电话与邮箱，支持重新指派角色")
    @PutMapping("/{userId}")
    public ApiResponse<UserAdminVo> updateUser(
            @Parameter(description = "用户唯一标识 ID", required = true)
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserUpdateRequest request) {
        UserAdminVo vo = userAdminService.updateUser(userId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 修改用户账号状态（正常/禁用/锁定）。
     * <p>
     * 【用途】供管理员启停用用户账号（具备自保护与最后管理员保护）。
     * 主要入参：userId (目标用户ID), request (目标状态)；
     * 返回结果：ApiResponse&lt;UserAdminVo&gt; 更新后的用户画像；
     * 简要流程：参数校验 -> 调用 service.updateUserStatus(userId, request)。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 状态修改请求体
     * @return 统一响应包装的用户视图对象
     */
    @Operation(summary = "变更用户账号状态", description = "启停用或锁定指定账号，具备禁止停用自身与禁止停用租户最后管理员保护")
    @PutMapping("/{userId}/status")
    public ApiResponse<UserAdminVo> updateUserStatus(
            @Parameter(description = "用户唯一标识 ID", required = true)
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        UserAdminVo vo = userAdminService.updateUserStatus(userId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 重置指定用户的登录密码。
     * <p>
     * 【用途】供管理员强制重置用户密码。
     * 主要入参：userId (目标用户ID), request (新密码明文)；
     * 返回结果：ApiResponse&lt;Void&gt; 成功响应；
     * 简要流程：参数校验 -> 调用 service.resetPassword(userId, request)。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 密码重置请求体
     * @return 统一成功响应
     */
    @Operation(summary = "重置用户密码", description = "强制重置指定用户的登录密码，并将该用户当前活跃会话强制下线")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @Parameter(description = "用户唯一标识 ID", required = true)
            @PathVariable("id") UUID userId,
            @Valid @RequestBody UserResetPasswordRequest request) {
        userAdminService.resetPassword(userId, request);
        return ApiResponse.ok();
    }

    /**
     * 重新分配用户所属角色。
     * <p>
     * 【用途】供管理员调整用户的角色授权。
     * 主要入参：userId (目标用户ID), request (角色ID列表)；
     * 返回结果：ApiResponse&lt;UserAdminVo&gt; 更新后的用户画像；
     * 简要流程：参数校验 -> 调用 service.assignRoles(userId, request)。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 角色分配请求体
     * @return 统一响应包装的用户视图对象
     */
    @Operation(summary = "分配用户角色", description = "全量重新分配用户的所属业务角色，并同步刷新其权限缓存")
    @PutMapping("/{userId}/roles")
    public ApiResponse<UserAdminVo> assignRoles(
            @Parameter(description = "用户唯一标识 ID", required = true)
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserRoleAssignRequest request) {
        UserAdminVo vo = userAdminService.assignRoles(userId, request);
        return ApiResponse.ok(vo);
    }

    /**
     * 删除指定用户账号（软删除）。
     * <p>
     * 【用途】供管理员移除废弃账号。
     * 主要入参：userId (目标用户ID)；
     * 返回结果：ApiResponse&lt;Void&gt; 成功响应；
     * 简要流程：调用 service.deleteUser(userId) 执行软删除并清理在线会话。
     * </p>
     *
     * @param userId 目标用户 ID
     * @return 统一成功响应
     */
    @Operation(summary = "删除用户账号", description = "软删除指定用户账号，具备禁止删除自身与禁止删除最后管理员保护")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(
            @Parameter(description = "用户唯一标识 ID", required = true)
            @PathVariable("userId") UUID userId) {
        userAdminService.deleteUser(userId);
        return ApiResponse.ok();
    }
}
