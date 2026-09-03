package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 身份认证与登录会话异常。
 * <p>
 * 当请求缺少 Token、Token 无效、Token 已过期或被踢下线时抛出，统一映射为 HTTP 401 Unauthorized。
 * </p>
 */
public class AuthException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用默认未认证提示构造异常。
     */
    public AuthException() {
        super(CommonErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 使用指定提示信息构造认证异常。
     *
     * @param message 认证失败提示信息
     */
    public AuthException(String message) {
        super(CommonErrorCode.UNAUTHORIZED.getCode(), message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 携带上游根因异常的构造函数。
     *
     * @param message 错误提示
     * @param cause   上游异常
     */
    public AuthException(String message, Throwable cause) {
        super(CommonErrorCode.UNAUTHORIZED.getCode(), message, HttpStatus.UNAUTHORIZED, null, cause);
    }
}
