package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.context.RequestContextHolder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局统一 REST 异常处理器。
 * <p>
 * 集中拦截服务端抛出的受控业务异常、框架校验异常、安全访问异常与未知系统异常，
 * 统一包装为符合规范的 {@link ApiResponse} 结构体并映射正确的 HTTP 状态码。
 * </p>
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理平台自定义业务异常体系（{@link BaseException} 及其子类）。
     *
     * @param ex 业务异常
     * @return 包含对应 HTTP 状态码的统一响应实体
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleBaseException(BaseException ex) {
        log.warn("[业务受控异常] requestId={}, code={}, httpStatus={}, message={}",
                RequestContextHolder.getRequestId(), ex.getCode(), ex.getHttpStatus(), ex.getMessage());

        ApiResponse<Object> response = new ApiResponse<>(
                ex.getCode(),
                ex.getMessage(),
                ex.getData(),
                RequestContextHolder.getRequestId(),
                null
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    /**
     * 处理 Spring MVC RequestBody 对象参数校验异常（{@link MethodArgumentNotValidException}）。
     *
     * @param ex 参数校验异常
     * @return 包含字段级错误信息的 422 统一响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        String summaryMessage = fieldErrors.values().stream().findFirst().orElse("请求参数校验不通过");
        log.warn("[参数校验异常] requestId={}, summary={}, errors={}",
                RequestContextHolder.getRequestId(), summaryMessage, fieldErrors);

        ApiResponse<Map<String, String>> response = ApiResponse.error(
                CommonErrorCode.VALIDATION_ERROR.getCode(),
                summaryMessage,
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * 处理表单绑定校验异常（{@link BindException}）。
     *
     * @param ex 绑定异常
     * @return 包含字段级错误信息的 422 统一响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBindException(BindException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        String summaryMessage = fieldErrors.values().stream().findFirst().orElse("请求数据绑定校验失败");
        ApiResponse<Map<String, String>> response = ApiResponse.error(
                CommonErrorCode.VALIDATION_ERROR.getCode(),
                summaryMessage,
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * 处理单参数校验异常（{@link ConstraintViolationException}）。
     *
     * @param ex 约束违反异常
     * @return 422 统一响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing
                ));

        String summaryMessage = violations.values().stream().findFirst().orElse("输入参数不符合约束条件");
        ApiResponse<Map<String, String>> response = ApiResponse.error(
                CommonErrorCode.VALIDATION_ERROR.getCode(),
                summaryMessage,
                violations
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * 处理 HTTP 请求体反序列化失败异常（如 JSON 语法错误或字段类型不匹配）。
     *
     * @param ex 反序列化异常
     * @return 400 统一响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("[HTTP消息读取异常] requestId={}, message={}", RequestContextHolder.getRequestId(), ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(
                CommonErrorCode.BAD_REQUEST.getCode(),
                "请求体解析失败，请检查 JSON 格式或参数类型"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理缺失必填查询参数异常。
     *
     * @param ex 缺失参数异常
     * @return 400 统一响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        String msg = String.format("缺失必填请求参数: %s", ex.getParameterName());
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.BAD_REQUEST.getCode(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理方法参数类型转换不匹配异常。
     *
     * @param ex 类型不匹配异常
     * @return 400 统一响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("参数 [%s] 格式或类型不正确: %s", ex.getName(), ex.getValue());
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.BAD_REQUEST.getCode(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理 HTTP 请求方法不支持异常。
     *
     * @param ex 请求方法不支持异常
     * @return 405 统一响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        String msg = String.format("不支持的 HTTP 请求方法: %s", ex.getMethod());
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED.getCode(), msg);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /**
     * 处理资源或接口未找到异常（Spring Boot 3.x 路由未匹配）。
     *
     * @param ex 资源未找到异常
     * @return 404 统一响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        String msg = String.format("请求的静态资源或接口不存在: %s", ex.getResourcePath());
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.NOT_FOUND.getCode(), msg);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理 Spring Security 权限不足异常。
     *
     * @param ex 权限不足异常
     * @return 403 统一响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("[访问拒绝] requestId={}, message={}", RequestContextHolder.getRequestId(), ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 处理 Spring Security 认证失败异常。
     *
     * @param ex 认证异常
     * @return 401 统一响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("[未认证异常] requestId={}, message={}", RequestContextHolder.getRequestId(), ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.UNAUTHORIZED);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 兜底处理未捕获的系统内部未知异常。
     *
     * @param ex 未知异常
     * @return 500 统一响应
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Throwable ex) {
        log.error("[系统未捕获严重异常] requestId={}", RequestContextHolder.getRequestId(), ex);
        ApiResponse<Void> response = ApiResponse.error(
                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
