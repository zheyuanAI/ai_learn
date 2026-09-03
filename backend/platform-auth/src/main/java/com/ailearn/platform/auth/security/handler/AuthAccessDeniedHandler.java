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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Spring Security 权限不足统一响应处理器。
 * <p>
 * 当已认证但缺乏具体权限点被拦截时，输出统一 403 JSON 结构。
 * </p>
 */
@Component
public class AuthAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public AuthAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        log.warn("[安全拦截] 权限不足访问 URI={}, message={}", request.getRequestURI(), accessDeniedException.getMessage());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(
                CommonErrorCode.FORBIDDEN.getCode(),
                "访问受保护资源失败：当前账号缺少该操作的功能权限点"
        );
        apiResponse.setRequestId(RequestContextHolder.getRequestId());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
