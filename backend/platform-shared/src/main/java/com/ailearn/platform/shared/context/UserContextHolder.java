package com.ailearn.platform.shared.context;

import com.ailearn.platform.shared.exception.AuthException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 当前登录用户安全上下文 ThreadLocal 持有者与便捷访问工具类。
 * <p>
 * 统一连接 {@link RequestContextHolder} 与 {@link UserContext}，提供线程安全的用户身份凭据存取、角色与权限判定。
 * </p>
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> USER_CONTEXT_HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * 获取当前线程绑定的 {@link UserContext}。
     * 若未直接设置过 UserContext，但 RequestContext 中有用户数据，则动态构建返回。
     *
     * @return 当前 {@link UserContext} 或 null
     */
    public static UserContext get() {
        UserContext uc = USER_CONTEXT_HOLDER.get();
        if (uc != null) {
            return uc;
        }

        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && (rc.getUserId() != null || rc.getUsername() != null)) {
            UserContext built = new UserContext();
            built.setUserId(rc.getUserId() != null ? rc.getUserId().toString() : null);
            built.setTenantId(rc.getTenantId() != null ? rc.getTenantId().toString() : null);
            built.setUsername(rc.getUsername());
            built.setSessionId(rc.getJti());
            built.setRequestId(rc.getRequestId());
            Set<String> auths = new HashSet<>();
            if (rc.getRoles() != null) {
                for (String r : rc.getRoles()) {
                    auths.add("ROLE_" + r);
                }
            }
            if (rc.getPermissions() != null) {
                auths.addAll(rc.getPermissions());
            }
            built.setAuthorities(auths);
            return built;
        }
        return null;
    }

    /**
     * 设置当前线程绑定的用户安全上下文，并同步至 {@link RequestContextHolder}。
     *
     * @param context 用户安全上下文
     */
    public static void set(UserContext context) {
        if (context == null) {
            USER_CONTEXT_HOLDER.remove();
        } else {
            USER_CONTEXT_HOLDER.set(context);
            // 同步至 RequestContextHolder
            RequestContext rc = RequestContextHolder.getContext();
            if (context.getUserId() != null) {
                try {
                    rc.setUserId(UUID.fromString(context.getUserId()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (context.getTenantId() != null) {
                try {
                    rc.setTenantId(UUID.fromString(context.getTenantId()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rc.setUsername(context.getUsername());
            rc.setJti(context.getSessionId());
            rc.setRequestId(context.getRequestId());
            rc.setRoles(context.getRoles());
            rc.setPermissions(context.getPermissions());
        }
    }

    /**
     * 获取当前登录用户 ID (UUID)。
     *
     * @return 用户 UUID，若未登录返回 null
     */
    public static UUID getUserId() {
        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && rc.getUserId() != null) {
            return rc.getUserId();
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        if (uc != null && uc.getUserId() != null) {
            try {
                return UUID.fromString(uc.getUserId());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户 ID (UUID)，若未登录则抛出 {@link AuthException}。
     *
     * @return 经过认证的用户 UUID
     * @throws AuthException 当用户未认证或会话已过期时抛出
     */
    public static UUID requireUserId() {
        UUID userId = getUserId();
        if (userId == null) {
            throw new AuthException("当前操作需要登录，未获取到有效用户上下文");
        }
        return userId;
    }

    /**
     * 获取当前登录用户 ID 字符串。
     *
     * @return 用户 ID 字符串或 null
     */
    public static String getUserIdString() {
        UUID userId = getUserId();
        if (userId != null) {
            return userId.toString();
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null ? uc.getUserId() : null;
    }

    /**
     * 获取当前租户 ID (UUID)。
     *
     * @return 租户 UUID 或 null
     */
    public static UUID getTenantId() {
        return RequestContextHolder.getTenantId();
    }

    /**
     * 获取当前租户 ID 字符串。
     *
     * @return 租户 ID 字符串或 null
     */
    public static String getTenantIdString() {
        UUID tenantId = getTenantId();
        if (tenantId != null) {
            return tenantId.toString();
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null ? uc.getTenantId() : null;
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 账号名或 null
     */
    public static String getUsername() {
        String username = RequestContextHolder.getUsername();
        if (username != null) {
            return username;
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null ? uc.getUsername() : null;
    }

    /**
     * 获取当前会话 ID (JTI)。
     *
     * @return 会话 ID 或 null
     */
    public static String getSessionId() {
        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && rc.getJti() != null) {
            return rc.getJti();
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null ? uc.getSessionId() : null;
    }

    /**
     * 获取当前请求链路追踪 ID。
     *
     * @return 请求追踪 ID 或 null
     */
    public static String getRequestId() {
        return RequestContextHolder.getRequestId();
    }

    /**
     * 获取当前用户的业务角色集合。
     *
     * @return 角色集合（不可为 null，若无角色返回空集）
     */
    public static Set<String> getRoles() {
        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && rc.getRoles() != null && !rc.getRoles().isEmpty()) {
            return rc.getRoles();
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null ? uc.getRoles() : Collections.emptySet();
    }

    /**
     * 获取当前用户的功能权限点集合。
     *
     * @return 权限点集合（不可为 null，若无权限返回空集）
     */
    public static Set<String> getPermissions() {
        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && rc.getPermissions() != null && !rc.getPermissions().isEmpty()) {
            return rc.getPermissions();
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null ? uc.getPermissions() : Collections.emptySet();
    }

    /**
     * 获取当前用户的所有 Authority 集合。
     *
     * @return Authority 集合
     */
    public static Set<String> getAuthorities() {
        UserContext uc = get();
        return uc != null ? uc.getAuthorities() : Collections.emptySet();
    }

    /**
     * 判断当前用户是否持有指定角色。
     *
     * @param role 角色标识
     * @return 若持有返回 true，否则返回 false
     */
    public static boolean hasRole(String role) {
        if (role == null) {
            return false;
        }
        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && rc.hasRole(role)) {
            return true;
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null && uc.hasRole(role);
    }

    /**
     * 判断当前用户是否持有指定权限点。
     *
     * @param permission 权限标识
     * @return 若持有返回 true，否则返回 false
     */
    public static boolean hasPermission(String permission) {
        if (permission == null) {
            return false;
        }
        RequestContext rc = RequestContextHolder.getNullableContext();
        if (rc != null && rc.hasPermission(permission)) {
            return true;
        }
        UserContext uc = USER_CONTEXT_HOLDER.get();
        return uc != null && uc.hasPermission(permission);
    }

    /**
     * 判断当前上下文是否已认证登录。
     *
     * @return 若用户 ID 不为空返回 true，否则返回 false
     */
    public static boolean isAuthenticated() {
        return getUserId() != null;
    }

    /**
     * 清理当前线程的所有安全上下文，防止线程池复用导致污染。
     */
    public static void clear() {
        USER_CONTEXT_HOLDER.remove();
        RequestContextHolder.clear();
    }
}
