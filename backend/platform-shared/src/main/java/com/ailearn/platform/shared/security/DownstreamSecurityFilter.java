package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.constants.HeaderConstants;
import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.UserContext;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 下游服务统一安全与身份上下文拦截过滤器。
 * <p>
 * Gateway 只透传用户、租户、用户名、会话和请求 ID；权限由集中式 Redis 快照读取，
 * 客户端提供的权限/角色 Header 永远不会成为授权来源。
 * </p>
 */
public class DownstreamSecurityFilter extends OncePerRequestFilter {

    private final PermissionContextReader permissionContextReader;
    private final ObjectMapper objectMapper;

    /**
     * 创建兼容单元测试的过滤器，缺少显式权限读取器时按无权限用户处理。
     * 生产 Spring Bean 使用带读取器的构造器，Redis 缺失由自动配置的 Fail-Closed 读取器返回 503。
     */
    public DownstreamSecurityFilter() {
        // 直接实例化过滤器的单元测试也要支持 ApiResponse 中的 JavaTime 字段。
        this((tenantId, userId) -> Collections.emptySet(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 创建下游安全过滤器。
     *
     * @param permissionContextReader 集中式权限快照读取器
     * @param objectMapper 统一 JSON 序列化器
     */
    public DownstreamSecurityFilter(PermissionContextReader permissionContextReader,
                                    ObjectMapper objectMapper) {
        this.permissionContextReader = permissionContextReader;
        this.objectMapper = objectMapper;
    }

    /**
     * 建立可信身份和权限上下文，并在请求结束后清理线程状态。
     * 入参为 Gateway 透传请求，出参为继续执行或已写入的统一错误响应；流程是解析身份、读取 Redis 权限、
     * 绑定 SecurityContext 后进入业务链，权限基础设施异常则在业务方法执行前返回 503。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain Servlet 过滤器链
     * @throws ServletException Servlet 过滤器异常
     * @throws IOException 响应写入异常
     */
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

        try {
            bindRequestMetadata(request, requestId);

            String userId = request.getHeader(HeaderConstants.X_USER_ID);
            String tenantId = request.getHeader(HeaderConstants.X_TENANT_ID);
            if (StringUtils.hasText(userId) || StringUtils.hasText(tenantId)) {
                UUID userUuid = parseRequiredUuid(userId, "用户身份");
                UUID tenantUuid = parseRequiredUuid(tenantId, "租户身份");
                String username = request.getHeader(HeaderConstants.X_USERNAME);
                String sessionId = request.getHeader(HeaderConstants.X_SESSION_ID);
                Set<String> permissions = permissionContextReader.readPermissions(tenantUuid, userUuid);
                bindAuthenticatedContext(requestId, userUuid, tenantUuid, username, sessionId, permissions);
            }

            filterChain.doFilter(request, response);
        } catch (ServiceUnavailableException ex) {
            // 权限缓存属于授权硬依赖，异常必须在业务方法执行前转为 503。
            writeServiceUnavailable(response, requestId, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // Gateway 正常不会产生非法 UUID；出现时按未认证请求处理，禁止建立任何业务上下文。
            writeUnauthorized(response, requestId, "认证身份格式无效");
        } finally {
            UserContextHolder.clear();
            RequestContextHolder.clear();
            SecurityContextHolder.clearContext();
            MDC.clear();
        }
    }

    /**
     * 绑定请求追踪和日志元数据。
     *
     * @param request 当前请求
     * @param requestId 请求追踪 ID
     */
    private void bindRequestMetadata(HttpServletRequest request, String requestId) {
        MDC.put("requestId", requestId);
        putMdcIfPresent("tenantId", request.getHeader(HeaderConstants.X_TENANT_ID));
        putMdcIfPresent("userId", request.getHeader(HeaderConstants.X_USER_ID));
        putMdcIfPresent("username", request.getHeader(HeaderConstants.X_USERNAME));

        RequestContext requestContext = new RequestContext();
        requestContext.setRequestId(requestId);
        requestContext.setClientIp(request.getRemoteAddr());
        RequestContextHolder.setContext(requestContext);
    }

    /**
     * 绑定已通过身份与权限读取的用户上下文。
     *
     * @param request 原始请求
     * @param requestId 请求追踪 ID
     * @param userId 用户 UUID
     * @param tenantId 租户 UUID
     * @param username 用户名
     * @param sessionId 会话 JTI
     * @param permissions Redis 返回的权限集合
     */
    private void bindAuthenticatedContext(String requestId,
                                          UUID userId,
                                          UUID tenantId,
                                          String username,
                                          String sessionId,
                                          Set<String> permissions) {
        Set<String> safePermissions = permissions == null ? Collections.emptySet() : permissions;
        UserContext userContext = new UserContext(
                userId.toString(), tenantId.toString(), username, sessionId, requestId, safePermissions);
        UserContextHolder.set(userContext);

        RequestContext requestContext = RequestContextHolder.getContext();
        requestContext.setTenantId(tenantId);
        requestContext.setUserId(userId);
        requestContext.setUsername(username);
        requestContext.setJti(sessionId);
        requestContext.setPermissions(safePermissions);

        List<SimpleGrantedAuthority> authorities = safePermissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        UserAuthenticationToken authentication = new UserAuthenticationToken(userContext, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 解析必须存在的 UUID Header。
     *
     * @param value Header 值
     * @param fieldName 字段名称
     * @return UUID
     * @throws IllegalArgumentException 缺失或格式不合法
     */
    private UUID parseRequiredUuid(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "缺失");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + "格式不合法", ex);
        }
    }

    /**
     * 写入非敏感 MDC 字段。
     *
     * @param key MDC 键
     * @param value Header 值
     */
    private void putMdcIfPresent(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        }
    }

    /**
     * 输出统一 503 响应。
     *
     * @param response 当前响应
     * @param requestId 请求追踪 ID
     * @param message 稳定错误信息
     * @throws IOException 写入响应失败
     */
    private void writeServiceUnavailable(HttpServletResponse response, String requestId, String message)
            throws IOException {
        writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, requestId,
                message != null ? message : "权限服务暂时不可用，请稍后重试");
    }

    /**
     * 输出统一 401 响应。
     *
     * @param response 当前响应
     * @param requestId 请求追踪 ID
     * @param message 稳定错误信息
     * @throws IOException 写入响应失败
     */
    private void writeUnauthorized(HttpServletResponse response, String requestId, String message)
            throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, requestId, message);
    }

    /**
     * 序列化统一错误响应。
     *
     * @param response 当前响应
     * @param status HTTP 状态码
     * @param requestId 请求追踪 ID
     * @param message 错误信息
     * @throws IOException 写入响应失败
     */
    private void writeError(HttpServletResponse response, int status, String requestId, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> apiResponse = ApiResponse.error(status, message);
        apiResponse.setRequestId(requestId);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
