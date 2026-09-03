package com.ailearn.platform.shared.filter;

import com.ailearn.platform.shared.context.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 全局链路追踪 Request-Id 过滤器。
 * <p>
 * 从入站 HTTP 请求头提取 {@code X-Request-Id}，若缺失则自动生成标准 UUID。
 * 将 Request-Id 同步注入至：
 * <ul>
 *   <li>响应头 {@code X-Request-Id}</li>
 *   <li>日志诊断上下文 {@link MDC} (键名为 "requestId")</li>
 *   <li>线程安全上下文 {@link RequestContextHolder}</li>
 * </ul>
 * 并在请求结束时完成 MDC 与 ThreadLocal 资源的彻底清理。
 * </p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * 请求头与响应头中的追踪 ID 键名
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * MDC 中保存追踪 ID 的键名
     */
    public static final String MDC_REQUEST_ID_KEY = "requestId";

    /**
     * 过滤处理核心逻辑：解析或生成 Request-Id，绑定上下文并在请求完成时清理。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        // 绑定至安全上下文与 MDC 日志
        RequestContextHolder.setRequestId(requestId);
        MDC.put(MDC_REQUEST_ID_KEY, requestId);

        // 回写至 HTTP 响应头
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
            RequestContextHolder.clear();
        }
    }
}
