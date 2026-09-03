package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 外部或依赖基础服务不可用异常（HTTP 503）。
 * <p>
 * 当下游 Redis、数据库或其他强依赖组件不可用或超时导致核心业务无法继续时抛出。
 * </p>
 */
public class ServiceUnavailableException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数（使用通用 503 错误信息）。
     */
    public ServiceUnavailableException() {
        super(CommonErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 自定义错误信息构造函数。
     *
     * @param message 详细错误描述信息
     */
    public ServiceUnavailableException(String message) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 携带根因异常的构造函数。
     *
     * @param message 详细错误描述信息
     * @param cause   根因异常
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), message, HttpStatus.SERVICE_UNAVAILABLE, null, cause);
    }
}
