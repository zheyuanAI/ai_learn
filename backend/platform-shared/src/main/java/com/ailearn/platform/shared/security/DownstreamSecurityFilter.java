package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.constants.HeaderConstants;
import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.UserContext;
import com.ailearn.platform.shared.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 下游服务统一安全与身份上下文拦截过滤器。
 * <p>
 * 从网关透传的受信任 HTTP 请求头中提取身份信息：
 * <ul>
 *   <li>X-User-Id：用户 ID</li>
 *   <li>X-Tenant-Id：租户 ID</li>
 *   <li>X-Username：用户名</li>
 *   <li>X-Session-Id：会话 JTI</li>
 *   <li>X-Authorities：权限/角色列表（逗号分隔）</li>
 *   <li>X-Request-Id：链路追踪 ID</li>
 * </ul>
 * 还原为 {@link UserContext} 与 {@link RequestContext} 并注入 Spring Security 上下文及 MDC 日志。
 * </p>
 */
public class DownstreamSecurityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(HeaderConstants.X_REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HeaderConstants.X_REQUEST_ID, requestId);

        String userId = request.getHeader(HeaderConstants.X_USER_ID);
        String tenantId = request.getHeader(HeaderConstants.X_TENANT_ID);
        String username = request.getHeader(HeaderConstants.X_USERNAME);
        String sessionId = request.getHeader(HeaderConstants.X_SESSION_ID);
        String authoritiesHeader = request.getHeader(HeaderConstants.X_AUTHORITIES);

        try {
            // 初始化日志链路 MDC
            MDC.put("requestId", requestId);
            if (StringUtils.hasText(tenantId)) {
                MDC.put("tenantId", tenantId);
            }
            if (StringUtils.hasText(userId)) {
                MDC.put("userId", userId);
            }
            if (StringUtils.hasText(username)) {
                MDC.put("username", username);
            }

            // 解析权限列表
            Set<String> authorities = parseAuthorities(authoritiesHeader);

            // 若存在有效用户标识，构建用户上下文并注入 Spring Security
            if (StringUtils.hasText(userId)) {
                UserContext userContext = new UserContext(userId, tenantId, username, sessionId, requestId, authorities);
                UserContextHolder.set(userContext);

                // 转换并同步 RequestContext
                RequestContext requestContext = new RequestContext();
                requestContext.setRequestId(requestId);
                requestContext.setUsername(username);
                requestContext.setJti(sessionId);
                requestContext.setClientIp(request.getRemoteAddr());
                requestContext.setRoles(userContext.getRoles());
                requestContext.setPermissions(userContext.getPermissions());
                try {
                    if (StringUtils.hasText(tenantId)) {
                        requestContext.setTenantId(UUID.fromString(tenantId));
                    }
                } catch (Exception ignored) {
                    // 若租户 ID 非 UUID 格式，作为属性保存
                    requestContext.setAttribute("rawTenantId", tenantId);
                }
                try {
                    if (StringUtils.hasText(userId)) {
                        requestContext.setUserId(UUID.fromString(userId));
                    }
                } catch (Exception ignored) {
                    // 若用户 ID 非 UUID 格式，作为属性保存
                    requestContext.setAttribute("rawUserId", userId);
                }
                RequestContextHolder.setContext(requestContext);

                // 构造 Spring Security 认证令牌
                List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                UserAuthenticationToken authentication = new UserAuthenticationToken(userContext, grantedAuthorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // 仅设置请求追踪 ID
                RequestContext requestContext = new RequestContext();
                requestContext.setRequestId(requestId);
                requestContext.setClientIp(request.getRemoteAddr());
                RequestContextHolder.setContext(requestContext);
            }

            filterChain.doFilter(request, response);
        } finally {
            // 清理上下文防止线程池污染
            UserContextHolder.clear();
            RequestContextHolder.clear();
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }

    /**
     * 解析逗号分隔的权限与角色字符串。
     *
     * @param authoritiesHeader 权限字符串
     * @return 权限与角色集合
     */
    private Set<String> parseAuthorities(String authoritiesHeader) {
        if (!StringUtils.hasText(authoritiesHeader)) {
            return Collections.emptySet();
        }
        return Arrays.stream(authoritiesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
