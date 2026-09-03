package com.ailearn.platform.auth.security.handler;

import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Spring Security 未认证统一响应处理器。
 * <p>
 * 当未提供令牌或令牌已失效被拒绝时，输出统一 401 JSON 结构。
 * </p>
 */
@Component
public class AuthAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(AuthAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public AuthAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        log.warn("[安全拦截] 未认证访问 URI={}, message={}", request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(
                CommonErrorCode.UNAUTHORIZED.getCode(),
                "访问受保护资源失败：未登录、令牌无效或会话已在其他终端登录"
        );
        apiResponse.setRequestId(RequestContextHolder.getRequestId());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
