package com.ailearn.platform.auth.service;

import com.ailearn.platform.auth.domain.dto.LoginRequest;
import com.ailearn.platform.auth.domain.vo.LoginResponse;
import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.domain.vo.UserProfileVo;
import java.util.List;
import java.util.UUID;

/**
 * 认证与权限核心业务服务接口。
 */
public interface AuthService {

    /**
     * 用户统一登录认证。
     * <p>
     * 校验所属租户与用户名密码，废弃旧会话（后登顶前），写入新会话事实与 Redis 会话，签发 RSA JWT。
     * </p>
     *
     * @param request   登录请求入参（tenantCode, username, password）
     * @param ipAddress 客户端来源 IP
     * @param userAgent 客户端 User-Agent
     * @return 包含 Token、JTI 和用户信息的响应 VO
     */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    /**
     * 用户注销登出。
     * <p>
     * 废弃数据库活跃会话记录并清除 Redis 会话与权限缓存。
     * </p>
     *
     * @param userId   当前登录用户 ID
     * @param tenantId 当前登录租户 ID
     */
    void logout(UUID userId, UUID tenantId);

    /**
     * 获取当前登录用户的全量个人信息、角色与功能权限点集合。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 用户全量资料 VO
     */
    UserProfileVo getCurrentUserProfile(UUID userId, UUID tenantId);

    /**
     * 获取当前登录用户角色关联的动态菜单树结构。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 嵌套组织的动态菜单树列表
     */
    List<MenuNodeVo> getCurrentUserMenus(UUID userId, UUID tenantId);
}
