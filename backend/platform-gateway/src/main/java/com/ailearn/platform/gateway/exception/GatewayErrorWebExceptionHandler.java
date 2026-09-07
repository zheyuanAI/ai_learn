package com.ailearn.platform.gateway.exception;

import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.constants.HeaderConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局统一异常处理器。
 * <p>
 * 捕获 401, 403, 404, 500, 503 等未捕获的网关层异常，输出统一的 {@link ApiResponse} 结构。
 * </p>
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将网关自身未捕获的异常转换为统一响应，并尽量沿用请求链路号。
     * <p>
     * 网关只负责 HTTP 状态和 {@link ApiResponse} 外壳，不在这里重判下游业务规则；响应已经提交时也不能再次改写，
     * 否则可能破坏下游已发送的响应流。无法映射为明确状态的异常统一按 500 处理，下游连接失败按 503 处理。
     * </p>
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String requestId = request.getHeaders().getFirst(HeaderConstants.X_REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        int code = 500;
        String message = "网关内部处理异常: " + ex.getMessage();

        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode statusCode = rse.getStatusCode();
            if (statusCode instanceof HttpStatus status) {
                httpStatus = status;
                code = status.value();
            } else {
                code = statusCode.value();
                httpStatus = HttpStatus.valueOf(code);
            }

            if (code == 404) {
                message = "网关未找到对应的服务或路由资源";
            } else if (code == 401) {
                message = "未认证或登录状态已失效";
            } else if (code == 403) {
                message = "无权访问该资源";
            } else if (code == 503) {
                message = "目标下游微服务暂时不可用，请稍后重试";
            } else {
                message = rse.getReason() != null ? rse.getReason() : httpStatus.getReasonPhrase();
            }
        } else if (ex instanceof java.net.ConnectException) {
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            code = 503;
            message = "目标下游微服务连接失败或服务未启动";
        }

        log.error("网关全局异常捕获 [{} {}]: status={}, message={}", request.getMethod(), request.getURI().getPath(), code, message, ex);

        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HeaderConstants.X_REQUEST_ID, requestId);

        ApiResponse<Void> apiResponse = ApiResponse.error(code, message);
        apiResponse.setRequestId(requestId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(apiResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"request_id\":\"" + requestId + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 将处理器置于网关错误链的高优先级，确保路由、连接和过滤器异常能先被统一包装。
     */
    @Override
    public int getOrder() {
        return -2;
    }
}
