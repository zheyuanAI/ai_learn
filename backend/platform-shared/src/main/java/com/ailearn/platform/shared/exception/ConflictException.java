package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 平台通用数据或状态冲突异常（HTTP 409 CONFLICT）。
 * <p>
 * 当请求的操作因当前资源状态不满足前置条件（例如：删除已分配用户的角色、删除含有子菜单或已被角色授权的菜单等）时抛出。
 * </p>
 */
public class ConflictException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用默认错误信息构造冲突异常。
     *
     * @param message 冲突原因描述
     */
    public ConflictException(String message) {
        super(CommonErrorCode.CONFLICT.getCode(), message, HttpStatus.CONFLICT);
    }

    /**
     * 使用指定错误码与自定义提示信息构造冲突异常。
     *
     * @param errorCode 错误码规范
     * @param message   冲突原因描述
     */
    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message, HttpStatus.CONFLICT);
    }

    /**
     * 使用自定义状态码与提示信息构造冲突异常。
     *
     * @param code    业务状态码
     * @param message 冲突原因描述
     */
    public ConflictException(int code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }

    /**
     * 携带详细上下文数据的全参构造函数。
     *
     * @param code    业务状态码
     * @param message 冲突原因描述
     * @param data    附带的冲突细节数据
     */
    public ConflictException(int code, String message, Object data) {
        super(code, message, HttpStatus.CONFLICT, data, null);
    }
}
