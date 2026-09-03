package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 接口幂等性冲突异常。
 * <p>
 * 当客户端使用相同 Idempotency-Key 重复发起请求，且前序请求正在处理中或已处理完成时触发，统一映射为 HTTP 409 Conflict。
 * </p>
 */
public class IdempotentConflictException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用默认幂等提示构造异常。
     */
    public IdempotentConflictException() {
        super(CommonErrorCode.IDEMPOTENT_CONFLICT, HttpStatus.CONFLICT);
    }

    /**
     * 使用指定提示信息构造幂等冲突异常。
     *
     * @param message 错误提示信息
     */
    public IdempotentConflictException(String message) {
        super(CommonErrorCode.IDEMPOTENT_CONFLICT.getCode(), message, HttpStatus.CONFLICT);
    }

    /**
     * 携带幂等 Key 及详情信息的构造函数。
     *
     * @param idempotencyKey 冲突的幂等键
     * @param message        错误描述
     */
    public IdempotentConflictException(String idempotencyKey, String message) {
        super(CommonErrorCode.IDEMPOTENT_CONFLICT.getCode(), message, HttpStatus.CONFLICT, idempotencyKey, null);
    }
}
