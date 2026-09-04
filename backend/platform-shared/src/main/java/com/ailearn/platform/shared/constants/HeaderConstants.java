package com.ailearn.platform.shared.constants;

/**
 * 平台通用 HTTP Header 常量定义。
 * <p>
 * 包含网关认证后向下游传递的受信任身份与追踪上下文 Header 键名。
 * </p>
 */
public final class HeaderConstants {

    private HeaderConstants() {
    }

    /**
     * 当前登录用户唯一 ID 请求头
     */
    public static final String X_USER_ID = "X-User-Id";

    /**
     * 当前操作租户 ID 请求头
     */
    public static final String X_TENANT_ID = "X-Tenant-Id";

    /**
     * 当前登录用户名请求头
     */
    public static final String X_USERNAME = "X-Username";

    /**
     * 当前登录会话唯一标识符 (JTI) 请求头
     */
    public static final String X_SESSION_ID = "X-Session-Id";

    /**
     * 历史权限请求头名称，仅用于网关清理和旧调用方编译兼容，禁止作为可信身份输入。
     */
    @Deprecated
    public static final String X_AUTHORITIES = "X-Authorities";

    /**
     * 历史权限请求头名称，仅用于网关清理，禁止作为可信身份输入。
     */
    public static final String X_PERMISSIONS = "X-Permissions";

    /**
     * 历史角色请求头名称，仅用于网关清理，禁止作为可信身份输入。
     */
    public static final String X_ROLES = "X-Roles";

    /**
     * 全局请求链路追踪唯一 ID 请求头
     */
    public static final String X_REQUEST_ID = "X-Request-Id";

    /**
     * 认证授权头部
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * Bearer Token 前缀
     */
    public static final String BEARER_PREFIX = "Bearer ";
}
