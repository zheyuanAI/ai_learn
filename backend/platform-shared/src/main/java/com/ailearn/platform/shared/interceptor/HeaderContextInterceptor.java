package com.ailearn.platform.shared.interceptor;

import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求头上下文拦截器。
 * <p>
 * 从网关（Gateway）转发的内部受信请求头中提取用户、租户、会话和权限元数据，并注入到 {@link RequestContextHolder}。
 * 支持的请求头包括：
 * <ul>
 *   <li>{@code X-Tenant-Id}：租户唯一 UUID</li>
 *   <li>{@code X-User-Id}：用户唯一 UUID</li>
 *   <li>{@code X-Username}：登录用户名（支持 URL 编码字符串）</li>
 *   <li>{@code X-Jti}：JWT 会话标识 JTI</li>
 *   <li>{@code X-Roles}：英文逗号分隔的角色编码列表</li>
 *   <li>{@code X-Permissions}：英文逗号分隔的权限点列表</li>
 * </ul>
 * </p>
 */
public class HeaderContextInterceptor implements HandlerInterceptor {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_JTI = "X-Jti";
    public static final String HEADER_ROLES = "X-Roles";
    public static final String HEADER_PERMISSIONS = "X-Permissions";

    /**
     * 前置处理：从请求头解析上下文信息并注入当前线程安全上下文。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  执行器
     * @return 总是返回 true 放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RequestContext context = RequestContextHolder.getContext();

        // 提取租户 ID
        String tenantIdStr = request.getHeader(HEADER_TENANT_ID);
        if (StringUtils.hasText(tenantIdStr)) {
            try {
                context.setTenantId(UUID.fromString(tenantIdStr.trim()));
            } catch (IllegalArgumentException ignored) {
                // 格式不合法时保留为 null
            }
        }

        // 提取用户 ID
        String userIdStr = request.getHeader(HEADER_USER_ID);
        if (StringUtils.hasText(userIdStr)) {
            try {
                context.setUserId(UUID.fromString(userIdStr.trim()));
            } catch (IllegalArgumentException ignored) {
                // 格式不合法时保留为 null
            }
        }

        // 提取用户名（若包含中文可能经过 URL 编码）
        String usernameStr = request.getHeader(HEADER_USERNAME);
        if (StringUtils.hasText(usernameStr)) {
            try {
                context.setUsername(URLDecoder.decode(usernameStr.trim(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                context.setUsername(usernameStr.trim());
            }
        }

        // 提取 JTI
        String jtiStr = request.getHeader(HEADER_JTI);
        if (StringUtils.hasText(jtiStr)) {
            context.setJti(jtiStr.trim());
        }

        // 提取角色列表
        String rolesStr = request.getHeader(HEADER_ROLES);
        if (StringUtils.hasText(rolesStr)) {
            Set<String> roles = Arrays.stream(rolesStr.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            context.setRoles(roles);
        }

        // 提取权限列表
        String permissionsStr = request.getHeader(HEADER_PERMISSIONS);
        if (StringUtils.hasText(permissionsStr)) {
            Set<String> permissions = Arrays.stream(permissionsStr.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            context.setPermissions(permissions);
        }

        // 记录客户端 IP
        context.setClientIp(resolveClientIp(request));

        return true;
    }

    /**
     * 解析客户端真实 IP。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端真实 IP 地址
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // 多级反向代理取第一个非 unknown 的 IP
            int index = ip.indexOf(',');
            return index != -1 ? ip.substring(0, index).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 完成处理钩子：请求结束后辅助清理或日志。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  执行器
     * @param ex       产生的异常（若有）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        // 请求结束的最终清理主要在 RequestIdFilter 中统一执行，此处保留扩展点
    }
}
