package com.ailearn.platform.auth.service.admin;

import com.ailearn.platform.auth.domain.dto.admin.UserCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserPageQueryRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserResetPasswordRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserRoleAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.PageResult;
import com.ailearn.platform.auth.domain.vo.admin.UserAdminVo;
import java.util.UUID;

/**
 * 用户后台管理业务服务接口。
 * <p>
 * 提供多条件分页查询、详情读取、新增用户（BCrypt 加密与唯一性防重）、修改用户、启停用、重置密码、角色分配与防自删/防停用最后管理员保护。
 * </p>
 */
public interface UserAdminService {

    /**
     * 多条件动态分页查询当前租户内的用户列表。
     * <p>
     * 【用途】供管理后台用户表格分页展示与模糊检索。
     * 主要入参：request (分页与筛选参数，支持用户名、姓名、工号、状态与角色ID过滤)；
     * 返回结果：PageResult&lt;UserAdminVo&gt; 用户分页结果（含角色信息与脱敏数据）；
     * 简要流程：提取租户 ID，执行 MyBatis-Plus 分页多条件联查，批量装配角色明细后返回。
     * </p>
     *
     * @param request 分页检索请求参数
     * @return 分页结果包装对象
     */
    PageResult<UserAdminVo> pageUsers(UserPageQueryRequest request);

    /**
     * 查询指定用户的详细信息。
     * <p>
     * 【用途】供管理后台查看或编辑特定用户基本画像与分配的角色。
     * 主要入参：userId (目标用户ID)；
     * 返回结果：UserAdminVo 用户详情（无密码密文字段）；
     * 简要流程：核验租户隔离，查询用户实体与关联的角色清单，组装 VO 返回。
     * </p>
     *
     * @param userId 目标用户唯一标识 ID
     * @return 用户管理视图对象
     */
    UserAdminVo getUserDetail(UUID userId);

    /**
     * 创建新用户账号。
     * <p>
     * 【用途】供管理员录入新员工账号、初始密码并分配初始角色。
     * 主要入参：request (包含 username, password, realName, userNo, email, phone, roleIds)；
     * 返回结果：创建成功后的 UserAdminVo 用户画像；
     * 简要流程：核验用户名与工号在租户内的唯一性，采用 BCrypt 强哈希加密密码，保存用户及用户角色关联。
     * </p>
     *
     * @param request 用户创建请求参数
     * @return 创建后的用户管理视图对象
     */
    UserAdminVo createUser(UserCreateRequest request);

    /**
     * 修改用户基本信息与角色。
     * <p>
     * 【用途】供管理员更新用户的真实姓名、工号、联系方式及重新指派角色。
     * 主要入参：userId (目标用户ID), request (修改字段集合)；
     * 返回结果：更新后的 UserAdminVo 用户画像；
     * 简要流程：核验租户与工号唯一性，更新用户基本字段，若传入角色列表则进行最后管理员保护校验并全量替换，清理受影响会话与权限缓存。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 用户修改请求参数
     * @return 更新后的用户管理视图对象
     */
    UserAdminVo updateUser(UUID userId, UserUpdateRequest request);

    /**
     * 变更用户账号状态（正常/禁用/锁定）。
     * <p>
     * 【用途】供管理员启停用或锁定用户账号，具备自保护与最后管理员保护。
     * 主要入参：userId (目标用户ID), request (目标状态 ACTIVE/DISABLED/LOCKED)；
     * 返回结果：更新后的 UserAdminVo；
     * 简要流程：阻断停用当前登录自身账号，阻断停用租户内最后一个活跃管理员，更新状态并强制踢出非 ACTIVE 状态用户的活跃会话。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 状态变更请求参数
     * @return 更新后的用户管理视图对象
     */
    UserAdminVo updateUserStatus(UUID userId, UserStatusUpdateRequest request);

    /**
     * 重置指定用户的登录密码。
     * <p>
     * 【用途】供管理员对忘记密码或密码泄露的用户执行强制密码重置。
     * 主要入参：userId (目标用户ID), request (新密码明文)；
     * 返回结果：无（操作成功即完成）；
     * 简要流程：核验租户隔离，BCrypt 哈希新密码并更新数据库，废除该用户的活跃会话以强制重新登录。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 重置密码请求参数
     */
    void resetPassword(UUID userId, UserResetPasswordRequest request);

    /**
     * 为指定用户重新分配所属角色列表。
     * <p>
     * 【用途】供管理员调整用户的角色授权。
     * 主要入参：userId (目标用户ID), request (目标角色ID列表)；
     * 返回结果：更新后的 UserAdminVo；
     * 简要流程：核验租户隔离与最后管理员保护，事务化全量替换 user_role 关系，清理 Redis 权限与菜单缓存。
     * </p>
     *
     * @param userId  目标用户 ID
     * @param request 角色分配请求参数
     * @return 更新后的用户管理视图对象
     */
    UserAdminVo assignRoles(UUID userId, UserRoleAssignRequest request);

    /**
     * 删除指定用户账号（软删除）。
     * <p>
     * 【用途】供管理员移除离职或废弃账号。
     * 主要入参：userId (目标用户ID)；
     * 返回结果：无；
     * 简要流程：阻断删除当前登录自身账号，阻断删除租户内最后一个活跃管理员，执行软删除，清理角色关联与在线会话缓存。
     * </p>
     *
     * @param userId 目标用户 ID
     */
    void deleteUser(UUID userId);
}
