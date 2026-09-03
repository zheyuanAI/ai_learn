package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 平台通用业务异常抽象基类。
 * <p>
 * 所有受控业务异常的基类，持有错误状态码、HTTP 状态映射、中文错误描述及可选的错误详情对象。
 * </p>
 */
public abstract class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码
     */
    private final int code;

    /**
     * 对应的 HTTP 状态码
     */
    private final HttpStatus httpStatus;

    /**
     * 错误附带数据（可选）
     */
    private final Object data;

    /**
     * 基于错误码和 HTTP 状态构造异常。
     *
     * @param errorCode  业务错误码规范
     * @param httpStatus 对应的 HTTP 状态
     */
    public BaseException(ErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = httpStatus;
        this.data = null;
    }

    /**
     * 基于错误码、自定义提示和 HTTP 状态构造异常。
     *
     * @param errorCode  业务错误码规范
     * @param message    自定义错误提示
     * @param httpStatus 对应的 HTTP 状态
     */
    public BaseException(ErrorCode errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = httpStatus;
        this.data = null;
    }

    /**
     * 基于自定义错误码、提示信息和 HTTP 状态构造异常。
     *
     * @param code       业务状态码
     * @param message    提示信息
     * @param httpStatus 对应的 HTTP 状态
     */
    public BaseException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = null;
    }

    /**
     * 携带详细上下文数据的全参构造函数。
     *
     * @param code       业务状态码
     * @param message    提示信息
     * @param httpStatus 对应的 HTTP 状态
     * @param data       异常详情数据
     * @param cause      上游根因异常
     */
    public BaseException(int code, String message, HttpStatus httpStatus, Object data, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    /**
     * 获取业务状态码。
     *
     * @return 整数型业务码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取 HTTP 状态枚举。
     *
     * @return {@link HttpStatus}
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 获取错误详情数据。
     *
     * @return 附带的结构化错误数据
     */
    public Object getData() {
        return data;
    }
}
