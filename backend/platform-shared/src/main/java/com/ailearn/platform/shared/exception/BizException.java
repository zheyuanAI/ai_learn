package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 通用业务逻辑异常。
 * <p>
 * 当业务规则校验未通过（例如库存不足、状态不允许流转、非法操作等）时抛出，默认映射为 HTTP 400 Bad Request。
 * </p>
 */
public class BizException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用默认提示构造通用业务异常。
     *
     * @param message 中文错误提示信息
     */
    public BizException(String message) {
        super(CommonErrorCode.BAD_REQUEST.getCode(), message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 使用指定错误码枚举构造业务异常。
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode, HttpStatus.BAD_REQUEST);
    }

    /**
     * 使用指定错误码枚举和自定义错误描述构造业务异常。
     *
     * @param errorCode 错误码枚举
     * @param message   覆盖默认提示的自定义信息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 自定义业务错误码与提示信息构造异常。
     *
     * @param code    自定义业务错误码
     * @param message 中文错误提示
     */
    public BizException(int code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 携带附带数据的全参构造函数。
     *
     * @param code    自定义业务状态码
     * @param message 错误提示
     * @param data    附带的详情数据
     */
    public BizException(int code, String message, Object data) {
        super(code, message, HttpStatus.BAD_REQUEST, data, null);
    }
}
