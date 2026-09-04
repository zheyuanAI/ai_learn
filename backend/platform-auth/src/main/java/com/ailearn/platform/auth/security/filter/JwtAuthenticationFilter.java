package com.ailearn.platform.auth.security.filter;

import com.ailearn.platform.auth.security.jwt.JwtTokenService;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 安全认证与单账号单有效会话拦截过滤器。
 * <p>
 * 核心流程：
 * 1. 提取 Authorization Header 中的 Bearer Token；
 * 2. RSA 公钥验签与过期校验；
 * 3. 校验 Redis auth:session:{tenantId}:{userId} 中的活跃 JTI 是否与 Token 中的 JTI 完全一致；
 *    若不一致或不存在，判定为旧会话被顶替（401 Displaced），阻断请求并返回统一 401 JSON；
 * 4. 组装多租户安全上下文 RequestContext 并注入 Spring Security 上下文。
 * </p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService jwtTokenService;
    private final SessionCacheService sessionCacheService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   SessionCacheService sessionCacheService,
                                   ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.sessionCacheService = sessionCacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. RSA 签名与时效解析校验
            JWTClaimsSet claims = jwtTokenService.parseAndVerify(token);
            UUID userId = UUID.fromString(claims.getSubject());
            UUID tenantId = UUID.fromString(claims.getStringClaim("tenant_id"));
            String username = claims.getStringClaim("username");
            String jti = claims.getJWTID();

            // 2. 单账号单有效会话核心控制：核验 Redis 活跃 JTI 是否匹配
            String activeJti = sessionCacheService.getActiveSessionJti(tenantId, userId);
            if (activeJti == null || !activeJti.equals(jti)) {
                log.warn("[单会话顶替拦截] userId={}, tokenJti={}, activeJti={}", userId, jti, activeJti);
                sendUnauthorizedError(response, "会话已失效或已在其他终端登录，请重新登录");
                return;
            }

            // 3. 只读取登录时预热的集中式权限快照；缺失时 Fail-Closed，不回源数据库。
            Set<String> perms = sessionCacheService.getCachedPermissions(tenantId, userId);
            if (perms == null) {
                throw new com.ailearn.platform.shared.exception.ServiceUnavailableException(
                        "权限服务暂时不可用，请稍后重试");
            }

            // 4. 组装 Spring Security 权限集；角色不再作为认证来源，业务统一使用 hasAuthority。
            ArrayList<GrantedAuthority> authorities = new ArrayList<>();
            for (String perm : perms) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }

            // 5. 绑定平台请求上下文
            RequestContext context = RequestContextHolder.getContext();
            context.setTenantId(tenantId);
            context.setUserId(userId);
            context.setUsername(username);
            context.setJti(jti);
            context.setRoles(new HashSet<>());
            context.setPermissions(new HashSet<>(perms));
            context.setClientIp(request.getRemoteAddr());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (com.ailearn.platform.shared.exception.ServiceUnavailableException ex) {
            log.error("[会话服务不可用] URI={}, error={}", request.getRequestURI(), ex.getMessage());
            sendServiceUnavailableError(response, ex.getMessage());
        } catch (Exception ex) {
            log.warn("[JWT认证异常] URI={}, error={}", request.getRequestURI(), ex.getMessage());
            sendUnauthorizedError(response, "访问令牌无效或已过期: " + ex.getMessage());
        } finally {
            RequestContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 发送 503 统一 JSON 错误响应。
     */
    private void sendServiceUnavailableError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), message);
        apiResponse.setRequestId(RequestContextHolder.getRequestId());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }

    /**
     * 发送 401 统一 JSON 错误响应。
     */
    private void sendUnauthorizedError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(CommonErrorCode.UNAUTHORIZED.getCode(), message);
        apiResponse.setRequestId(RequestContextHolder.getRequestId());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
