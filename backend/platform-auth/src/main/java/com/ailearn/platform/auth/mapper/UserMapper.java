package com.ailearn.platform.auth.mapper;

import com.ailearn.platform.auth.domain.dto.admin.UserPageQueryRequest;
import com.ailearn.platform.auth.domain.entity.Role;
import com.ailearn.platform.auth.domain.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper 数据访问接口。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据租户 ID 与用户名查找未删除的用户。
     *
     * @param tenantId 租户 ID
     * @param username 登录账号名
     * @return 用户实体或 null
     */
    default User findByTenantIdAndUsername(UUID tenantId, String username) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUsername, username));
    }

    /**
     * 根据用户 ID 与租户 ID 查找处于可用状态且未删除的用户（防止跨租户越权）。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 用户实体或 null
     */
    default User findByUserIdAndTenantId(UUID userId, UUID tenantId) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getTenantId, tenantId)
                .eq(User::getStatus, "ACTIVE"));
    }

    /**
     * 根据用户 ID 与租户 ID 查找用户（无论状态，仅限本租户未删除用户）。
     * <p>
     * 【新增方法】用于管理后台读取特定用户详细信息或进行状态变更。
     * 主要入参：userId (用户标识), tenantId (租户标识)；
     * 返回结果：包含禁用/锁定在内的本租户 User 实体，若不存在或跨租户则为 null；
     * 简要流程：按 tenant_id 与 user_id 执行等值查询。
     * </p>
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 用户实体或 null
     */
    default User findAnyStatusUserByIdAndTenantId(UUID userId, UUID tenantId) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getTenantId, tenantId));
    }

    /**
     * 校验租户内用户名是否已被其他未删除用户占用。
     * <p>
     * 【新增方法】用于新建/修改用户时的账号名唯一性冲突校验。
     * 主要入参：tenantId (租户标识), username (待校验用户名), excludeUserId (排除的用户ID，新建传 null)；
     * 返回结果：true 表示已存在冲突，false 表示可用；
     * 简要流程：统计同租户、未删除且排除当前用户 ID 的重名记录数。
     * </p>
     *
     * @param tenantId      租户 ID
     * @param username      登录名
     * @param excludeUserId 排除的用户 ID（可为 null）
     * @return 若已存在返回 true，否则 false
     */
    default boolean existsByUsername(UUID tenantId, String username, UUID excludeUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUsername, username);
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return selectCount(wrapper) > 0;
    }

    /**
     * 校验租户内员工工号是否已被其他未删除用户占用。
     * <p>
     * 【新增方法】用于新建/修改用户时的工号唯一性冲突校验。
     * 主要入参：tenantId (租户标识), userNo (待校验工号), excludeUserId (排除的用户ID，新建传 null)；
     * 返回结果：true 表示已存在冲突，false 表示可用；
     * 简要流程：工号非空时统计同租户、未删除且排除当前用户 ID 的重复记录数。
     * </p>
     *
     * @param tenantId      租户 ID
     * @param userNo        员工工号
     * @param excludeUserId 排除的用户 ID（可为 null）
     * @return 若已存在返回 true，否则 false
     */
    default boolean existsByUserNo(UUID tenantId, String userNo, UUID excludeUserId) {
        if (userNo == null || userNo.trim().isEmpty()) {
            return false;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getUserNo, userNo.trim());
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return selectCount(wrapper) > 0;
    }

    /**
     * 根据用户 ID 与租户 ID 联查用户关联的角色实体列表（XML SQL 实现）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 角色实体列表
     */
    List<Role> findRolesByUserIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 根据用户 ID 与租户 ID 联查用户关联的角色业务编码列表（XML SQL 实现）。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return 角色编码字符串列表
     */
    List<String> findRoleCodesByUserIdAndTenantId(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 分页多条件动态检索租户内用户列表（XML SQL 实现）。
     * <p>
     * 【新增方法】用于后台管理用户表格分页展示与多维度条件检索。
     * 主要入参：page (分页参数), tenantId (当前租户ID), req (检索过滤条件)；
     * 返回结果：IPage 分页用户记录；
     * 简要流程：动态拼接 username/realName/userNo 模糊匹配、status 精确匹配及 roleId 角色关联过滤。
     * </p>
     *
     * @param page     MyBatis-Plus 分页对象
     * @param tenantId 租户 ID
     * @param req      分页检索条件
     * @return 用户分页结果
     */
    IPage<User> selectUserPage(Page<User> page, @Param("tenantId") UUID tenantId, @Param("req") UserPageQueryRequest req);

    /**
     * 统计指定租户下处于 ACTIVE 状态的管理员用户总数（XML SQL 实现）。
     * <p>
     * 【新增方法】用于防停用/防删除最后一个管理员的安全保护机制。
     * 主要入参：tenantId (租户ID)；
     * 返回结果：处于正常状态的租户管理员用户数量；
     * 简要流程：联查 auth_user 与 auth_user_role 及 auth_role，筛选 role_code 为 tenant.admin 或 TENANT_ADMIN 的正常用户数量。
     * </p>
     *
     * @param tenantId 租户 ID
     * @return 活跃管理员总数
     */
    int countActiveAdmins(@Param("tenantId") UUID tenantId);
}
